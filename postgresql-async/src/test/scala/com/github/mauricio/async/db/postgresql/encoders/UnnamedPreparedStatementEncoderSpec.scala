package com.github.mauricio.async.db.postgresql.encoders

import com.github.mauricio.async.db.Spec
import com.github.mauricio.async.db.postgresql.column.PostgreSQLColumnEncoderRegistry
import com.github.mauricio.async.db.postgresql.messages.frontend.UnnamedPreparedStatementMessage
import io.netty.util.CharsetUtil

class UnnamedPreparedStatementEncoderSpec extends Spec {

  val registry = new PostgreSQLColumnEncoderRegistry()
  val encoder = new UnnamedPreparedStatementEncoder(CharsetUtil.UTF_8, registry)

  val sampleMessage = Array[Byte](
    80, 0, 0, 0, 31, 0, 115, 101, 108, 101, 99, 116, 32, 42, 32, 102, 114, 111,
    109, 32, 117, 115, 101, 114, 115, 0, 0, 1, 0, 0, 0, 0, 66, 0, 0, 0, 16, 0,
    0, 0, 0, 0, 1, -1, -1, -1, -1, 0, 0, 68, 0, 0, 0, 6, 80, 0, 69, 0, 0, 0, 9,
    0, 0, 0, 0, 0, 83, 0, 0, 0, 4
  )

  "UnnamedPreparedStatementEncoder" - {

    "encode an unnamed extended query without an explicit close" in {
      val message = new UnnamedPreparedStatementMessage(
        "select * from users",
        List(Some(null)),
        registry
      )

      val result = encoder.encode(message)

      val bytes = new Array[Byte](result.readableBytes())
      result.readBytes(bytes)

      bytes === sampleMessage
    }

  }

}
