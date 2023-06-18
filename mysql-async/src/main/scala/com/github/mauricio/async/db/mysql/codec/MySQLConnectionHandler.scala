/*
 * Copyright 2013 Maurício Linhares
 *
 * Maurício Linhares licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.github.mauricio.async.db.mysql.codec

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.exceptions.DatabaseException
import com.github.mauricio.async.db.general.ResultSetBuilder
import com.github.mauricio.async.db.mysql.binary.BinaryRowDecoder
import com.github.mauricio.async.db.mysql.message.client._
import com.github.mauricio.async.db.mysql.message.server._
import com.github.mauricio.async.db.mysql.util.CharsetMapper
import com.github.mauricio.async.db.util.ChannelFutureTransformer.toFuture
import com.github.mauricio.async.db.util._
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, ByteBufAllocator, Unpooled}
import io.netty.channel._
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.CodecException
import scala.annotation.switch
import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.collection.immutable.IndexedSeq
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.collection.mutable.{Seq => MSeq}

class MySQLConnectionHandler(
  configuration: Configuration,
  charsetMapper: CharsetMapper,
  handlerDelegate: MySQLHandlerDelegate,
  group: EventLoopGroup,
  executionContext: ExecutionContext,
  connectionId: String
) extends SimpleChannelInboundHandler[Object] {

  private implicit val internalPool: ExecutionContext = executionContext
  private final val log = Log.getByName(s"[connection-handler]")
  private final val bootstrap         = new Bootstrap().group(this.group)
  private final val connectionPromise = Promise[MySQLConnectionHandler]()
  private final val decoder =
    new MySQLFrameDecoder(configuration.charset, connectionId)
  private final val encoder =
    new MySQLOneToOneEncoder(configuration.charset, charsetMapper)
  private final val sendLongDataEncoder = new SendLongDataEncoder()
  private var currentColumns            = Vector.empty[ColumnDefinitionMessage]
  private final val parsedStatements =
    new HashMap[String, PreparedStatementHolder]()
  private final val binaryRowDecoder = new BinaryRowDecoder()

  private var currentPreparedStatementHolder: PreparedStatementHolder = null
  private var currentPreparedStatement: PreparedStatement             = null
  private var currentQuery: ResultSetBuilder[ColumnDefinitionMessage] = null
  private var currentContext: ChannelHandlerContext                   = null

  def connect: Future[MySQLConnectionHandler] = {
    this.bootstrap.channel(classOf[NioSocketChannel])
    this.bootstrap.handler(new ChannelInitializer[io.netty.channel.Channel]() {

      override def initChannel(channel: io.netty.channel.Channel): Unit = {
        channel.pipeline.addLast(
          decoder,
          encoder,
          sendLongDataEncoder,
          MySQLConnectionHandler.this
        )
      }

    })

    this.bootstrap.option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
    this.bootstrap.option[ByteBufAllocator](
      ChannelOption.ALLOCATOR,
      LittleEndianByteBufAllocator.INSTANCE
    )

    this.bootstrap
      .connect(new InetSocketAddress(configuration.host, configuration.port))
      .failed
      .foreach { case exception =>
        this.connectionPromise.tryFailure(exception)
      }

    this.connectionPromise.future
  }

  override def channelRead0(
    ctx: ChannelHandlerContext,
    message: Object
  ): Unit = {
    message match {
      case m: ServerMessage => {
        (m.kind: @switch) match {
          case ServerMessage.ServerProtocolVersion => {
            handlerDelegate.onHandshake(m.asInstanceOf[HandshakeMessage])
          }
          case ServerMessage.Ok => {
            this.clearQueryState
            handlerDelegate.onOk(m.asInstanceOf[OkMessage])
          }
          case ServerMessage.Error => {
            this.clearQueryState
            handlerDelegate.onError(m.asInstanceOf[ErrorMessage])
          }
          case ServerMessage.EOF => {
            this.handleEOF(m)
          }
          case ServerMessage.ColumnDefinition => {
            val message = m.asInstanceOf[ColumnDefinitionMessage]

            if (
              currentPreparedStatementHolder != null && this.currentPreparedStatementHolder.needsAny
            ) {
              this.currentPreparedStatementHolder.add(message)
            }

            this.currentColumns = this.currentColumns :+ message
          }
          case ServerMessage.ColumnDefinitionFinished => {
            this.onColumnDefinitionFinished()
          }
          case ServerMessage.PreparedStatementPrepareResponse => {
            this.onPreparedStatementPrepareResponse(
              m.asInstanceOf[PreparedStatementPrepareResponse]
            )
          }
          case ServerMessage.Row => {
            val message = m.asInstanceOf[ResultSetRowMessage]
            val items   = new Array[Any](message.size)

            var x = 0
            while (x < message.size) {
              items(x) = if (message(x) == null) {
                null
              } else {
                val buf = message(x)
                try {
                  val columnDescription = this.currentQuery.columnTypes(x)
                  columnDescription.textDecoder.decode(
                    columnDescription,
                    buf,
                    configuration.charset
                  )
                } finally {
                  buf.release()
                }
              }
              x += 1
            }

            this.currentQuery.addRow(items)
          }
          case ServerMessage.BinaryRow => {
            val message = m.asInstanceOf[BinaryRowMessage]
            try {
              this.currentQuery.addRow(
                this.binaryRowDecoder
                  .decode(message.buffer, this.currentColumns)
              )
            } finally {
              message.buffer.release()
            }
          }
          case ServerMessage.ParamProcessingFinished => {}
          case ServerMessage.ParamAndColumnProcessingFinished => {
            this.onColumnDefinitionFinished()
          }
        }
      }
    }

  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    log.debug("Channel became active")
    handlerDelegate.connected(ctx)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    log.debug("Channel became inactive")
  }

  override def exceptionCaught(
    ctx: ChannelHandlerContext,
    cause: Throwable
  ): Unit = {
    // unwrap CodecException if needed
    cause match {
      case t: CodecException => handleException(t.getCause)
      case _                 => handleException(cause)
    }

  }

  private def handleException(cause: Throwable): Unit = {
    if (!this.connectionPromise.isCompleted) {
      this.connectionPromise.failure(cause)
    }
    handlerDelegate.exceptionCaught(cause)
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    this.currentContext = ctx
  }

  def write(message: QueryMessage): ChannelFuture = {
    this.decoder.queryProcessStarted()
    writeAndHandleError(message)
  }

  def sendPreparedStatement(
    query: String,
    values: Seq[Any]
  ): Future[ChannelFuture] = {
    val preparedStatement = new PreparedStatement(query, values)

    this.currentColumns = Vector.empty

    this.currentPreparedStatement = preparedStatement

    this.parsedStatements.get(preparedStatement.statement) match {
      case Some(item) => {
        this.executePreparedStatement(
          item.statementId,
          item.columns.size,
          preparedStatement.values,
          item.parameters
        )
      }
      case None => {
        decoder.preparedStatementPrepareStarted()
        writeAndHandleError(
          new PreparedStatementPrepareMessage(preparedStatement.statement)
        )
      }
    }
  }

  def write(message: HandshakeResponseMessage): ChannelFuture = {
    decoder.hasDoneHandshake = true
    writeAndHandleError(message)
  }

  def write(message: AuthenticationSwitchResponse): ChannelFuture =
    writeAndHandleError(message)

  def write(message: QuitMessage): ChannelFuture = {
    writeAndHandleError(message)
  }

  def disconnect: ChannelFuture = this.currentContext.close()

  def clearQueryState: Unit = {
    this.currentColumns = Vector.empty
    this.currentQuery = null
  }

  def isConnected: Boolean = {
    if (this.currentContext != null && this.currentContext.channel() != null) {
      this.currentContext.channel.isActive
    } else {
      false
    }
  }

  private def executePreparedStatement(
    statementId: Array[Byte],
    columnsCount: Int,
    values: Seq[Any],
    parameters: MSeq[ColumnDefinitionMessage]
  ): Future[ChannelFuture] = {
    decoder.preparedStatementExecuteStarted(columnsCount, parameters.size)
    this.currentColumns = Vector.empty

    val (nonLongIndicesOpt, longValuesOpt) = values.zipWithIndex.map {
      case (Some(value), index) if isLong(value) => (None, Some(index, value))
      case (value, index) if isLong(value)       => (None, Some(index, value))
      case (_, index)                            => (Some(index), None)
    }.unzip
    val nonLongIndices: Seq[Int]    = nonLongIndicesOpt.flatten
    val longValues: Seq[(Int, Any)] = longValuesOpt.flatten

    if (longValues.nonEmpty) {
      val (firstIndex, firstValue) = longValues.head
      var channelFuture: Future[ChannelFuture] =
        sendLongParameter(statementId, firstIndex, firstValue)
      longValues.tail foreach { case (index, value) =>
        channelFuture = channelFuture.flatMap { _ =>
          sendLongParameter(statementId, index, value)
        }
      }
      channelFuture flatMap { _ =>
        writeAndHandleError(
          new PreparedStatementExecuteMessage(
            statementId,
            values,
            nonLongIndices.toSet,
            parameters
          )
        )
      }
    } else {
      writeAndHandleError(
        new PreparedStatementExecuteMessage(
          statementId,
          values,
          nonLongIndices.toSet,
          parameters
        )
      )
    }
  }

  private def isLong(value: Any): Boolean = {
    value match {
      case v: Array[Byte] => v.length > SendLongDataEncoder.LONG_THRESHOLD
      case v: ByteBuffer  => v.remaining() > SendLongDataEncoder.LONG_THRESHOLD
      case v: ByteBuf => v.readableBytes() > SendLongDataEncoder.LONG_THRESHOLD

      case _ => false
    }
  }

  private def sendLongParameter(
    statementId: Array[Byte],
    index: Int,
    longValue: Any
  ): Future[ChannelFuture] = {
    longValue match {
      case v: Array[Byte] =>
        sendBuffer(Unpooled.wrappedBuffer(v), statementId, index)

      case v: ByteBuffer =>
        sendBuffer(Unpooled.wrappedBuffer(v), statementId, index)

      case v: ByteBuf =>
        sendBuffer(v, statementId, index)
    }
  }

  private def sendBuffer(
    buffer: ByteBuf,
    statementId: Array[Byte],
    paramId: Int
  ): ChannelFuture = {
    writeAndHandleError(new SendLongDataMessage(statementId, buffer, paramId))
  }

  private def onPreparedStatementPrepareResponse(
    message: PreparedStatementPrepareResponse
  ): Unit = {
    this.currentPreparedStatementHolder = new PreparedStatementHolder(
      this.currentPreparedStatement.statement,
      message
    )
  }

  def onColumnDefinitionFinished(): Unit = {

    val columns = if (this.currentPreparedStatementHolder != null) {
      this.currentPreparedStatementHolder.columns
    } else {
      this.currentColumns
    }

    this.currentQuery = new ResultSetBuilder[ColumnDefinitionMessage](columns)

    if (this.currentPreparedStatementHolder != null) {
      this.parsedStatements.put(
        this.currentPreparedStatementHolder.statement,
        this.currentPreparedStatementHolder
      )
      this.executePreparedStatement(
        this.currentPreparedStatementHolder.statementId,
        this.currentPreparedStatementHolder.columns.size,
        this.currentPreparedStatement.values,
        this.currentPreparedStatementHolder.parameters
      )
      this.currentPreparedStatementHolder = null
      this.currentPreparedStatement = null
    }
  }

  private def writeAndHandleError(message: Any): ChannelFuture = {
    if (this.currentContext.channel().isActive) {
      val res = this.currentContext.writeAndFlush(message)

      res.failed.foreach { case e: Throwable =>
        handleException(e)
      }

      res
    } else {
      val error = new DatabaseException(
        "This channel is not active and can't take messages"
      )
      handleException(error)
      this.currentContext.channel().newFailedFuture(error)
    }
  }

  private def handleEOF(m: ServerMessage): Unit = {
    m match {
      case eof: EOFMessage => {
        val resultSet = this.currentQuery
        this.clearQueryState

        if (resultSet != null) {
          handlerDelegate.onResultSet(resultSet.build(), eof)
        } else {
          handlerDelegate.onEOF(eof)
        }
      }
      case authenticationSwitch: AuthenticationSwitchRequest => {
        handlerDelegate.switchAuthentication(authenticationSwitch)
      }
    }
  }

  def schedule(block: => Unit, duration: Duration): Unit = {
    this.currentContext
      .channel()
      .eventLoop()
      .schedule(
        new Runnable {
          override def run(): Unit = block
        },
        duration.toMillis,
        TimeUnit.MILLISECONDS
      )
  }

}
