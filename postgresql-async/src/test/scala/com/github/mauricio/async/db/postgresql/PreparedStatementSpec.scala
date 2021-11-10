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

package com.github.mauricio.async.db.postgresql

import com.github.mauricio.async.db.Spec
import org.joda.time.LocalDate
import com.github.mauricio.async.db.util.Log
import com.github.mauricio.async.db.exceptions.InsufficientParametersException
import java.util.UUID

import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.Random
import scala.collection.compat.immutable.ArraySeq

class PreparedStatementSpec extends Spec with DatabaseTestHelper {

  val log = Log.get[PreparedStatementSpec]

  val filler = List.fill(64)(" ").mkString("")

  val messagesCreate = """CREATE TEMP TABLE messages
                         (
                           id bigserial NOT NULL,
                           content character varying(255) NOT NULL,
                           moment date NULL,
                           CONSTRAINT bigserial_column_pkey PRIMARY KEY (id )
                         )"""
  val messagesInsert =
    s"INSERT INTO messages $filler (content,moment) VALUES (?,?) RETURNING id"
  val messagesInsertReverted =
    s"INSERT INTO messages $filler (moment,content) VALUES (?,?) RETURNING id"
  val messagesUpdate =
    "UPDATE messages SET content = ?, moment = ? WHERE id = ?"
  val messagesSelectOne =
    "SELECT id, content, moment FROM messages WHERE id = ?"
  val messagesSelectByMoment =
    "SELECT id, content, moment FROM messages WHERE moment = ?"
  val messagesSelectAll = "SELECT id, content, moment FROM messages"
  val messagesSelectEscaped =
    "SELECT id, content, moment FROM messages WHERE content LIKE '%??%' AND id > ?"

  "prepared statements" - {

    "support prepared statement with more than 64 characters" in {
      withHandler { handler =>
        val firstContent  = "Some Moment"
        val secondContent = "Some Other Moment"
        val date          = LocalDate.now()

        executeDdl(handler, this.messagesCreate)
        executePreparedStatement(
          handler,
          this.messagesInsert,
          Array(firstContent, date)
        )
        executePreparedStatement(
          handler,
          this.messagesInsertReverted,
          Array(date, secondContent)
        )

        val rows =
          executePreparedStatement(handler, this.messagesSelectAll).rows.get

        rows.length mustEqual 2

        rows(0)("id") mustEqual 1
        rows(0)("content") mustEqual firstContent
        rows(0)("moment") mustEqual date

        rows(1)("id") mustEqual 2
        rows(1)("content") mustEqual secondContent
        rows(1)("moment") mustEqual date

      }
    }

    "execute a prepared statement without any parameters multiple times" in {

      withHandler { handler =>
        executeDdl(handler, this.messagesCreate)
        executePreparedStatement(
          handler,
          "UPDATE messages SET content = content"
        )
        executePreparedStatement(
          handler,
          "UPDATE messages SET content = content"
        )
        succeed
      }

    }

    "raise an exception if the parameter count is different from the given parameters count" in {

      withHandler { handler =>
        executeDdl(handler, this.messagesCreate)
        a[
          InsufficientParametersException
        ] must be thrownBy executePreparedStatement(
          handler,
          this.messagesSelectOne
        )
      }

    }

    "run two different prepared statements in sequence and get the right values" in {

      val create =
        """CREATE TEMP TABLE other_messages
                         (
                           id bigserial NOT NULL,
                           other_moment date NULL,
                           other_content character varying(255) NOT NULL,
                           CONSTRAINT other_messages_bigserial_column_pkey PRIMARY KEY (id )
                         )"""

      val select = "SELECT * FROM other_messages"
      val insert =
        "INSERT INTO other_messages (other_moment, other_content) VALUES (?, ?)"

      val moment      = LocalDate.now()
      val otherMoment = LocalDate.now().minusDays(10)

      val message      = "this is some message"
      val otherMessage = "this is some other message"

      withHandler { handler =>
        executeDdl(handler, this.messagesCreate)
        executeDdl(handler, create)

        forAll((1 until 4).toList) { (x: Int) =>
          executePreparedStatement(
            handler,
            this.messagesInsert,
            Array(message, moment)
          )
          executePreparedStatement(
            handler,
            insert,
            Array(otherMoment, otherMessage)
          )

          val result =
            executePreparedStatement(handler, this.messagesSelectAll).rows.get
          result.size mustEqual x
          result.columnNames must contain inOrder ("id", "content", "moment")
          result(x - 1)("moment") mustEqual moment
          result(x - 1)("content") mustEqual message

          val otherResult = executePreparedStatement(handler, select).rows.get
          otherResult.size mustEqual x
          otherResult.columnNames must contain inOrder ("id", "other_moment", "other_content")

          otherResult(x - 1)("other_moment") mustEqual otherMoment
          otherResult(x - 1)("other_content") mustEqual otherMessage
        }

      }

    }

    "support prepared statement with Option parameters (Some/None)" in {
      withHandler { handler =>
        val firstContent  = "Some Moment"
        val secondContent = "Some Other Moment"
        val date          = LocalDate.now()

        executeDdl(handler, this.messagesCreate)
        executePreparedStatement(
          handler,
          this.messagesInsert,
          Array(Some(firstContent), None)
        )
        executePreparedStatement(
          handler,
          this.messagesInsert,
          Array(Some(secondContent), Some(date))
        )

        val rows =
          executePreparedStatement(handler, this.messagesSelectAll).rows.get

        rows.length mustEqual 2

        rows(0)("id") mustEqual 1
        rows(0)("content") mustEqual firstContent
        rows(0)("moment") mustEqual (null: Any)

        rows(1)("id") mustEqual 2
        rows(1)("content") mustEqual secondContent
        rows(1)("moment") mustEqual date
      }
    }

    "supports sending null first and then an actual value for the fields" in {
      withHandler { handler =>
        val firstContent  = "Some Moment"
        val secondContent = "Some Other Moment"
        val date          = LocalDate.now()

        executeDdl(handler, this.messagesCreate)
        executePreparedStatement(
          handler,
          this.messagesInsert,
          Array(firstContent, null)
        )
        executePreparedStatement(
          handler,
          this.messagesInsert,
          Array(secondContent, date)
        )

        val rows = executePreparedStatement(
          handler,
          this.messagesSelectByMoment,
          Array(null)
        ).rows.get
        rows.size mustEqual 0

        /*
          PostgreSQL does not know how to handle NULL parameters for a query in a prepared statement,
          you have to use IS NULL if you want to make use of it.

          rows.length mustEqual 1

          rows(0)("id") mustEqual 1
          rows(0)("content") mustEqual firstContent
          rows(0)("moment") mustEqual null
         */

        val rowsWithoutNull = executePreparedStatement(
          handler,
          this.messagesSelectByMoment,
          Array(date)
        ).rows.get
        rowsWithoutNull.size mustEqual 1
        rowsWithoutNull(0)("id") mustEqual 2
        rowsWithoutNull(0)("content") mustEqual secondContent
        rowsWithoutNull(0)("moment") mustEqual date
      }
    }

    "support prepared statement with escaped placeholders" in {
      withHandler { handler =>
        val firstContent  = "Some? Moment"
        val secondContent = "Some Other Moment"
        val date          = LocalDate.now()

        executeDdl(handler, this.messagesCreate)
        executePreparedStatement(
          handler,
          this.messagesInsert,
          Array(Some(firstContent), None)
        )
        executePreparedStatement(
          handler,
          this.messagesInsert,
          Array(Some(secondContent), Some(date))
        )

        val rows = executePreparedStatement(
          handler,
          this.messagesSelectEscaped,
          Array(0)
        ).rows.get

        rows.length mustEqual 1

        rows(0)("id") mustEqual 1
        rows(0)("content") mustEqual firstContent
        rows(0)("moment") mustEqual (null: Any)

      }
    }

    "support handling of enum types" in {

      withHandler { handler =>
        val create = """CREATE TEMP TABLE messages
                         |(
                         |id bigserial NOT NULL,
                         |feeling example_mood,
                         |CONSTRAINT bigserial_column_pkey PRIMARY KEY (id )
                         |);""".stripMargin
        val insert = "INSERT INTO messages (feeling) VALUES (?) RETURNING id"
        val select = "SELECT * FROM messages"

        executeDdl(handler, create)

        executePreparedStatement(handler, insert, Array("sad"))

        val result = executePreparedStatement(handler, select).rows.get

        result.size mustEqual 1
        result(0)("id") mustEqual 1L
        result(0)("feeling") mustEqual "sad"
      }

    }

    "support handling JSON type" in {

      if (System.getenv("TRAVIS") == null) {
        withHandler { handler =>
          val create = """create temp table people
                           |(
                           |id bigserial primary key,
                           |addresses json,
                           |phones json
                           |);""".stripMargin

          val insert =
            "INSERT INTO people (addresses, phones) VALUES (?,?) RETURNING id"
          val select = "SELECT * FROM people"
          val addresses =
            """[ {"Home" : {"city" : "Tahoe", "state" : "CA"}} ]"""
          val phones = """[ "925-575-0415", "916-321-2233" ]"""

          executeDdl(handler, create)
          executePreparedStatement(handler, insert, Array(addresses, phones))
          val result = executePreparedStatement(handler, select).rows.get

          result(0)("addresses") mustEqual addresses
          result(0)("phones") mustEqual phones
        }
        succeed
      } else {
        pending
      }
    }

    "support select bind value" in {
      withHandler { handler =>
        val string = "someString"
        val result = executePreparedStatement(
          handler,
          "SELECT CAST(? AS VARCHAR)",
          Array(string)
        ).rows.get
        result(0)(0) mustEqual string
      }
    }

    "fail if prepared statement has more variables than it was given" in {
      withHandler { handler =>
        executeDdl(handler, messagesCreate)
        an[InsufficientParametersException] must be thrownBy {
          handler.sendPreparedStatement(
            "SELECT * FROM messages WHERE content = ? AND moment = ?",
            List("some content")
          )
        }
      }
    }

    "run prepared statement twice with bad and good values" in {
      withHandler { handler =>
        val content = "Some Moment"

        val query = "SELECT content FROM messages WHERE id = ?"

        executeDdl(handler, messagesCreate)
        executePreparedStatement(
          handler,
          this.messagesInsert,
          Array(Some(content), None)
        )
        a[GenericDatabaseException] must be thrownBy {
          executePreparedStatement(
            handler,
            query,
            Array("undefined")
          )
        }
        val result = executePreparedStatement(handler, query, Array(1)).rows.get
        result(0)(0) mustEqual content
      }
    }

    "support UUID" in {
      if (System.getenv("TRAVIS") == null) {
        withHandler { handler =>
          val create = """create temp table uuids
                           |(
                           |id bigserial primary key,
                           |my_id uuid
                           |);""".stripMargin

          val insert = "INSERT INTO uuids (my_id) VALUES (?) RETURNING id"
          val select = "SELECT * FROM uuids"

          val uuid = UUID.randomUUID()

          executeDdl(handler, create)
          executePreparedStatement(handler, insert, Array(uuid))
          val result = executePreparedStatement(handler, select).rows.get

          result(0)("my_id").asInstanceOf[UUID] mustEqual uuid
        }
        succeed
      } else {
        pending
      }
    }

    "support UUID array" in {
      if (System.getenv("TRAVIS") == null) {
        withHandler { handler =>
          val create = """create temp table uuids
                           |(
                           |id bigserial primary key,
                           |my_id uuid[]
                           |);""".stripMargin

          val insert = "INSERT INTO uuids (my_id) VALUES (?) RETURNING id"
          val select = "SELECT * FROM uuids"

          val uuid1 = UUID.randomUUID()
          val uuid2 = UUID.randomUUID()

          executeDdl(handler, create)
          executePreparedStatement(handler, insert, Array(Array(uuid1, uuid2)))
          val result = executePreparedStatement(handler, select).rows.get
          result(0)("my_id").asInstanceOf[Seq[Any]] mustEqual Seq(
            uuid1,
            uuid2
          )
        }
        succeed
      } else {
        pending
      }
    }

    "not take twice the time as a non prepared statement" in {
      withHandler { handler =>
        executeDdl(
          handler,
          "create temp table performance_test (id integer PRIMARY KEY, int1 integer)"
        )
        (1 to 2000).foreach(i =>
          executeQuery(
            handler,
            s"insert into performance_test (id, int1) values ($i, ${Random.nextInt(20000)})"
          )
        )

        val preparedStatementStartTime = System.nanoTime()
        (1 to 2000).foreach { i =>
          val id = Random.nextInt(2000)
          Await.result(
            handler.sendPreparedStatement(
              "update performance_test set int1 = int1 where id = ?",
              ArraySeq(id)
            ),
            Duration(5, SECONDS)
          )
        }
        val preparedStatementTime = System
          .nanoTime() - preparedStatementStartTime

        val plainQueryStartTime = System.nanoTime()
        (1 to 2000).foreach { i =>
          val id = Random.nextInt(2000)
          Await.result(
            handler.sendQuery(
              s"update performance_test set int1 = int1 where id = $id"
            ),
            Duration(5, SECONDS)
          )
        }
        val plainQueryTime = System.nanoTime() - plainQueryStartTime

        preparedStatementTime must be < (plainQueryTime * 2)
      }
    }
  }

}
