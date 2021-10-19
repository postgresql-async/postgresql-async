package com.github.mauricio.async.db.mysql

import com.github.mauricio.async.db.Spec
import scala.concurrent.duration.Duration
import com.github.mauricio.async.db.RowData

class ZeroDatesSpec extends Spec with ConnectionHelper {

  val createStatement =
    """CREATE TEMPORARY TABLE dates (
      |`name` varchar (255) NOT NULL,
      |`timestamp_column` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
      |`date_column` date NOT NULL DEFAULT '0000-00-00',
      |`datetime_column` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
      |`time_column` time NOT NULL DEFAULT '00:00:00',
      |`year_column` year NOT NULL DEFAULT '0000'
      |)
      |ENGINE=MyISAM DEFAULT CHARSET=utf8;""".stripMargin

  val insertStatement = "INSERT INTO dates (name) values ('Joe')"
  val selectStatement = "SELECT * FROM dates"

  def matchValues(result: RowData) = {
    result("name") === "Joe"
    result("timestamp_column") must be(null: Any)
    result("datetime_column") must be(null: Any)
    result("date_column") must be(null: Any)
    result("year_column") === 0
    result("time_column") === Duration.Zero
  }

  "client" - {

    "correctly parse the MySQL zeroed dates as NULL values in text protocol" in {

      withConnection { connection =>
        executeQuery(connection, "SET sql_mode = '';")
        executeQuery(connection, createStatement)
        executeQuery(connection, insertStatement)

        matchValues(executeQuery(connection, selectStatement).rows.get(0))
      }
    }

    "correctly parse the MySQL zeroed dates as NULL values in binary protocol" in {

      withConnection { connection =>
        executeQuery(connection, "SET sql_mode = '';")
        executeQuery(connection, createStatement)
        executeQuery(connection, insertStatement)

        matchValues(
          executePreparedStatement(connection, selectStatement).rows.get(0)
        )
      }
    }

  }

}
