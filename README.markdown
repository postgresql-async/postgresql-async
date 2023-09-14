## Table of contents
- [Include them as dependencies](#include-them-as-dependencies)
- [Database connections and encodings](#database-connections-and-encodings)
- [Prepared statements gotcha](#prepared-statements-gotcha)
- [What are the design goals?](#what-are-the-design-goals)
- [What is missing?](#what-is-missing)
- [How can you help?](#how-can-you-help)
- [Main public interface](#main-public-interface)
  - [Connection](#connection)
  - [QueryResult](#queryresult)
  - [ResultSet](#resultset)
  - [Prepared statements](#prepared-statements)
- [Transactions](#transactions)
- [Example usage (for PostgreSQL, but it looks almost the same on MySQL)](#example-usage-for-postgresql-but-it-looks-almost-the-same-on-mysql)
- [LISTEN/NOTIFY support (PostgreSQL only)](#listennotify-support-postgresql-only)
- [Contributing](#contributing)
- [Licence](#licence)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

[![build](https://github.com/postgresql-async/postgresql-async/actions/workflows/scala.yml/badge.svg?branch=master)](https://github.com/postgresql-async/postgresql-async/actions/workflows/scala.yml)
[![codecov](https://codecov.io/gh/postgresql-async/postgresql-async/branch/master/graph/badge.svg?token=UR9HMLK0L2)](https://codecov.io/gh/postgresql-async/postgresql-async)
[![Maven](https://maven-badges.herokuapp.com/maven-central/com.github.postgresql-async/postgresql-async_2.13/badge.svg)](https://search.maven.org/artifact/com.github.postgresql-async/postgresql-async_2.13)


This is a fork of the orginal `postgresal-async` lib.

It currently cross compiled with `2.11` / `2.12` / `2.13` and `3.x`

This project currently returns [JodaTime](http://joda-time.sourceforge.net/), it will be replace by `java.time` in the near future.
`java.util.Date` class.

For information specific to the drivers, check 
 - [PostgreSQL README](postgresql-async/README.md) and the
 - [MySQL README](mysql-async/README.md).

## Include them as dependencies

And if you're in a hurry, you can include them in your build like this, if you're using PostgreSQL:

```scala
"com.github.postgresql-async" %% "postgresql-async" % "0.3.6"
```

Or Maven:

```xml
<dependency>
  <groupId>com.github.postgresql-async</groupId>
  <artifactId>postgresql-async_<scalaMajorVersion></artifactId>
  <version>0.3.6</version>
</dependency>
```

And if you're into MySQL:

```scala
"com.github.postgresql-async" %% "mysql-async" % "0.3.6"
```

Or Maven:

```xml
<dependency>
  <groupId>com.github.postgresql-async</groupId>
  <artifactId>mysql-async_<scalaMajorVersion></artifactId>
  <version>0.3.6</version>
</dependency>
```


## Database connections and encodings

**READ THIS NOW**

Both clients will let you set the database encoding for something else. Unless you are **1000% sure** of what you are doing,
**DO NOT** change the default encoding (currently, UTF-8). Some people assume the connection encoding is the **database**
or **columns** encoding but **IT IS NOT**, this is just the connection encoding that is used between client and servers
doing communication.

When you change the encoding of the **connection** you are not affecting your database's encoding and your columns
**WILL NOT** be stored with the connection encoding. If the connection and database/column encoding is different, your
database will automatically translate from the connection encoding to the correct encoding and all your data will be
safely stored at your database/column encoding.

This is as long as you are using the correct string types, BLOB columns will not be translated since they're supposed
to hold a stream of bytes.

So, just don't touch it, create your tables and columns with the correct encoding and be happy.

## Prepared statements gotcha

If you have used JDBC before, you might have heard that prepared statements are the best thing on earth when talking
to databases. This isn't exactly true all the time (as you can see on [this presentation](http://www.youtube.com/watch?v=kWOAHIpmLAI)
by [@tenderlove](http://github.com/tenderlove)) and there is a memory cost in keeping prepared statements.

Prepared statements are tied to a connection, they are not database-wide, so, if you generate your queries dynamically
all the time you might eventually blow up your connection memory and your database memory.

Why?

Because when you create a prepared statement, locally, the connection keeps the prepared statement description in memory.
This can be the returned columns information, input parameters information, query text, query identifier that will be
used to execute the query and other flags. This also causes a data structure to be created at your server **for every
connection**.

So, prepared statements are awesome, but are not free. Use them judiciously.

## What are the design goals?

- fast, fast and faster
- small memory footprint
- avoid copying data as much as possible (we're always trying to use wrap and slice on buffers)
- easy to use, call a method, get a future or a callback and be happy
- never, ever, block
- all features covered by tests
- less dependencies (it currently depends on Netty, JodaTime, scala-collection-compat and SFL4J only)

## Recently planned improvoments

- [x] CI/CD migration
- [x] Scala 3 support
- [ ] `java.time` migration
- [ ] Stablize api
- [ ] New pool implementation
- [ ] PreparedStatement management
- [ ] ColumnData caching
- [ ] `PooledByteBufferAllocator` support
- [ ] More netty customization option (inlcuding native transport)
- [ ] Postgresql binary protocol


## How can you help?

- checkout the source code
- find bugs, find places where performance can be improved
- check the **What is missing** piece
- check the [issues page](https://github.com/postgresql-async/postgresql-async/issues) for bugs or new features
- send a pull request with specs

## Main public interface

### Connection

Represents a connection to the database. This is the **root** object you will be using in your application. You will
find three classes that implement this trait, `PostgreSQLConnection`, `MySQLConnection` and `ConnectionPool`.
The difference between them is that `ConnectionPool` is, as the name implies, a pool of connections and you
need to give it an connection factory so it can create connections and manage them.

To create both you will need a `Configuration` object with your database details. You can create one manually or
create one from a JDBC or Heroku database URL using the `URLParser` object.

### QueryResult

It's the output of running a statement against the database (either using `sendQuery` or `sendPreparedStatement`).
This object will contain the amount of rows, status message and the possible `ResultSet` (Option\[ResultSet]) if the
query returns any rows.

### ResultSet

It's an IndexedSeq\[Array\[Any]], every item is a row returned by the database. The database types are returned as Scala
objects that fit the original type, so `smallint` becomes a `Short`, `numeric` becomes `BigDecimal`, `varchar` becomes
`String` and so on. You can find the whole default transformation list at the project specific documentation.

### Prepared statements

Databases support prepared or precompiled statements. These statements allow the database to precompile the query
on the first execution and reuse this compiled representation on future executions, this makes it faster and also allows
for safer data escaping when dealing with user provided data.

To execute a prepared statement you grab a connection and:

```scala
val connection : Connection = ...
val future = connection.sendPreparedStatement( "SELECT * FROM products WHERE products.name = ?", Array( "Dominion" ) )
```

The `?` (question mark) in the query is a parameter placeholder, it allows you to set a value in that place in the
query without having to escape stuff yourself. The driver itself will make sure this parameter is delivered to the
database in a safe way so you don't have to worry about SQL injection attacks.

The basic numbers, Joda Time date, time, timestamp objects, strings and arrays of these objects are all valid values
as prepared statement parameters and they will be encoded to their respective database types. Remember that not all databases
are created equal, so not every type will work or might work in unexpected ways. For instance, MySQL doesn't have array
types, so, if you send an array or collection to MySQL it won't work.

Remember that parameters are positional the order they show up at query should be the same as the one in the array or
sequence given to the method call.

## Transactions

Both drivers support transactions at the database level, the isolation level is the default for your database/connection,
to change the isolation level just call your database's command to set the isolation level for what you want.

Here's an example of how transactions work:

```scala
  val future = connection.inTransaction {
    c =>
    c.sendPreparedStatement(this.insert)
     .flatMap( r => c.sendPreparedStatement(this.insert))
  }
```

The `inTransaction` method allows you to execute a collection of statements in a single transactions, just use the
connection object you will receive in your block and send your statements to it. Given each statement causes a new
future to be returned, you need to `flatMap` the calls to be able to get a `Future[T]` instead of `Future[Future[...]]`
 back.

If all futures succeed, the transaction is committed normally, if any of them fail, a `rollback` is issued to the
database. You should not reuse a database connection that has rolled back a transaction, just close it and create a
new connection to continue using it.

## Example usage (for PostgreSQL, but it looks almost the same on MySQL)

You can find a small Play 2 app using it [here](https://github.com/postgresql/postgresql-async-app) and a blog post about
it [here](http://postgresql.github.io/2013/04/29/async-database-access-with-postgresql-play-scala-and-heroku.html).

In short, what you would usually do is:
```scala
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import com.github.mauricio.async.db.postgresql.util.URLParser
import com.github.mauricio.async.db.{RowData, QueryResult, Connection}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global // Or use the default pool provided by underlying runtime.
import scala.concurrent.{Await, Future}

object BasicExample {

  def main(args: Array[String]) {

    val configuration = URLParser.parse("jdbc:postgresql://localhost:5233/my_database?user=postgres&password=somepassword")
    val connection: Connection = new PostgreSQLConnection(configuration)

    Await.result(connection.connect, 5 seconds)

    val future: Future[QueryResult] = connection.sendQuery("SELECT 0")

    val mapResult: Future[Any] = future.map(queryResult => queryResult.rows match {
      case Some(resultSet) => {
        val row : RowData = resultSet.head
        row(0)
      }
      case None => -1
    }
    )

    val result = Await.result( mapResult, 5 seconds ) // Blocking here, used for demo, should be avoid in real application.

    println(result)

    connection.disconnect

  }

}
```

First, create a `PostgreSQLConnection`, connect it to the database, execute queries (this object is not thread
safe, so you must execute only one query at a time) and work with the futures it returns. Once you are done, call
disconnect and the connection is closed.

You can also use the `ConnectionPool` provided by the driver to simplify working with database connections in your app.
Check the blog post above for more details and the project's ScalaDocs.

## LISTEN/NOTIFY support (PostgreSQL only)

LISTEN/NOTIFY is a PostgreSQL-specific feature for database-wide publish-subscribe scenarios. You can listen to database
notifications as such:

```scala
  val connection: Connection = ...

  connection.sendQuery("LISTEN my_channel")
  connection.registerNotifyListener {
    message =>
    println(s"channel: ${message.channel}, payload: ${message.payload}")
  }
```

## Contributing

Contributing to the project is simple, fork it on Github, hack on what you're insterested in seeing done or at the
bug you want to fix and send a pull request back. If you thing the change is too big or requires architectural changes
please create an issue **before** you start working on it so we can discuss what you're trying to do.

You should be easily able to build this project in your favorite IDE since it's built by [SBT](http://www.scala-sbt.org/)
using a plugin that generates your IDE's project files. You can use [sbt-idea](https://github.com/mpeltonen/sbt-idea)
for IntelliJ Idea and [sbteclipse](https://github.com/typesafehub/sbteclipse) for Eclipse integration.

[Check our list of contributors!](https://github.com/postgresql/postgresql-async/graphs/contributors)

## Licence

This project is freely available under the Apache 2 licence, fork, fix and send back :)
