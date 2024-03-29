package com.github.mauricio.async.db.mysql

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.Spec

/**
 * To run this spec you have to use the Vagrant file provided with the base
 * project and you have to start MySQL there. The expected MySQL version is
 * 5.1.73. Make sure the bootstrap.sh script is run, if it isn't, manually run
 * it yourself.
 */
class ClientPluginAuthDisabledSpec extends Spec with ConnectionHelper {

  "connection" - {

    "connect and query the database without a password" in {

      if (System.getenv("GITHUB_ACTIONS") == null) {
        withConnection { connection =>
          executeQuery(connection, "select version()")
          succeed
        }
      } else {
        succeed
      }

    }

    "connect and query the database with a password" in {

      if (System.getenv("GITHUB_ACTIONS") == null) {
        withConfigurableConnection(vagrantConfiguration) { connection =>
          executeQuery(connection, "select version()")
          succeed
        }
      } else {
        succeed
      }

    }

  }

  override def defaultConfiguration = new Configuration(
    "root",
    "127.0.0.1",
    port = 3307
  )

  def vagrantConfiguration = new Configuration(
    "mysql_vagrant",
    "localhost",
    port = 3307,
    password = Some("generic_password")
  )

}
