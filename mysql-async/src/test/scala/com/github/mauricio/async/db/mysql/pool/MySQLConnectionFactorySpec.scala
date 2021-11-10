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

package com.github.mauricio.async.db.mysql.pool

import com.github.mauricio.async.db.mysql.{MySQLConnection, ConnectionHelper}
import com.github.mauricio.async.db.util.FutureUtils.awaitFuture
import com.github.mauricio.async.db.Spec
import scala.util._
import com.github.mauricio.async.db.exceptions.ConnectionNotConnectedException
import scala.util.Failure

class MySQLConnectionFactorySpec extends Spec with ConnectionHelper {

  val factory = new MySQLConnectionFactory(this.defaultConfiguration)

  "factory" - {

    "fail validation if a connection has errored" in {

      val connection = factory.create

      val result = Try {
        executeQuery(connection, "this is not sql")
      }

      try {
        if (factory.validate(connection).isSuccess) {
          throw new IllegalStateException("should not have come here")
        }
      } finally {
        awaitFuture(connection.close)
      }

      succeed
    }

    "it should take a connection from the pool and the pool should not accept it back if it is broken" in {
      withPool { pool =>
        val connection = awaitFuture(pool.take)

        pool.inUse.size mustEqual 1

        awaitFuture(connection.disconnect)

        try {
          awaitFuture(pool.giveBack(connection))
        } catch {
          case e: ConnectionNotConnectedException => {
            // all good
          }
        }

        pool.inUse.size mustEqual 0

      }
    }

    "be able to provide connections to the pool" in {
      withPool { pool =>
        executeQuery(pool, "SELECT 0").rows.get(0)(0) mustEqual 0
      }
    }

    "fail validation if a connection is disconnected" in {
      val connection = factory.create

      awaitFuture(connection.disconnect)

      factory.validate(connection).isFailure must be(true)
    }

    "fail validation if a connection is still waiting for a query" in {
      val connection = factory.create
      connection.sendQuery("SELECT SLEEP(10)")

      Thread.sleep(1000)

      factory.validate(connection) match {
        case Failure(e) => succeed
        case Success(c) => fail("should not have come here")
      }

      awaitFuture(connection.close) mustEqual connection
    }

    "accept a good connection" in {
      val connection = factory.create

      factory.validate(connection) match {
        case Success(c) => succeed
        case Failure(e) => fail("should not have come here")
      }

      awaitFuture(connection.close) mustEqual connection
    }

    "test a valid connection and say it is ok" in {

      val connection = factory.create

      factory.test(connection) match {
        case Success(c) => succeed
        case Failure(e) => fail("should not have come here")
      }

      awaitFuture(connection.close) mustEqual connection

    }

    "fail test if a connection is disconnected" in {
      val connection = factory.create

      awaitFuture(connection.disconnect)

      factory.test(connection).isFailure must be(true)
    }

  }

}
