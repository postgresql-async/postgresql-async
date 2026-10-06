package com.github.mauricio.async.db.postgresql

import com.github.mauricio.async.db.{Spec, SslContexts}
import javax.net.ssl.SSLHandshakeException
import java.io.File

class PostgreSQLSSLConnectionSpec extends Spec with DatabaseTestHelper {

  "ssl handler" - {

    "connect to the database in ssl without verifying CA" in {

      withSSLHandler(sslContext = Some(SslContexts.insecureSslContext)) {
        handler =>
          handler.isReadyForQuery must be(true)
      }

    }

    "connect to the database in ssl verifying CA" in {

      withSSLHandler(
        host = "127.0.0.1",
        sslContext = Some(SslContexts.trustedSslContext(Some(serverCert)))
      ) { handler =>
        handler.isReadyForQuery must be(true)
      }

    }

    "connect to the database in ssl verifying CA and hostname" in {

      withSSLHandler(
        sslContext = Some(
          SslContexts.trustedSslContext(Some(serverCert), verifyHostname = true)
        )
      ) { handler =>
        handler.isReadyForQuery must be(true)
      }

    }

    "throws exception when CA verification fails" in {
      an[SSLHandshakeException] must be thrownBy {
        withSSLHandler(
          host = "127.0.0.1",
          sslContext = Some(SslContexts.trustedSslContext(None))
        ) { handler => }
      }

    }

    "throws exception when hostname verification fails" in {
      an[SSLHandshakeException] must be thrownBy {
        withSSLHandler(
          host = "127.0.0.1",
          sslContext = Some(
            SslContexts.trustedSslContext(
              Some(serverCert),
              verifyHostname = true
            )
          )
        ) { handler => }
      }
    }

    "connect with a client certificate (mutual TLS)" in {
      val sslContext = SslContexts.clientSslContext(
        rootCert = Some(serverCert),
        clientCert = new File("../build/client/cert/client.crt"),
        clientKey = new File("../build/client/cert/client.key")
      )
      withSSLHandler(
        host = "127.0.0.1",
        username = "postgres_cert",
        password = None,
        sslContext = Some(sslContext)
      ) { handler =>
        handler.isReadyForQuery must be(true)
      }
    }

    "fail mutual TLS connection without a client certificate" in {
      an[Exception] must be thrownBy {
        withSSLHandler(
          host = "127.0.0.1",
          username = "postgres_cert",
          password = None,
          sslContext = Some(SslContexts.trustedSslContext(Some(serverCert)))
        ) { handler => }
      }
    }

  }

}
