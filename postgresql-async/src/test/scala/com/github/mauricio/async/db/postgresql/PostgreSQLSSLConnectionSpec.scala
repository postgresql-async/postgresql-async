package com.github.mauricio.async.db.postgresql

import com.github.mauricio.async.db.Spec
import com.github.mauricio.async.db.SSLConfiguration.Mode
import javax.net.ssl.SSLHandshakeException

class PostgreSQLSSLConnectionSpec extends Spec with DatabaseTestHelper {

  "ssl handler" - {

    "connect to the database in ssl without verifying CA" in {

      withSSLHandler(Mode.Require, "127.0.0.1", None) { handler =>
        handler.isReadyForQuery must be(true)
      }

    }

    "connect to the database in ssl verifying CA" in {

      withSSLHandler(Mode.VerifyCA, "127.0.0.1") { handler =>
        handler.isReadyForQuery must be(true)
      }

    }

    "connect to the database in ssl verifying CA and hostname" in {

      withSSLHandler(Mode.VerifyFull) { handler =>
        handler.isReadyForQuery must be(true)
      }

    }

    "throws exception when CA verification fails" in {
      an[SSLHandshakeException] must be thrownBy {
        withSSLHandler(Mode.VerifyCA, rootCert = None) { handler => }
      }

    }

    "throws exception when hostname verification fails" in {
      an[SSLHandshakeException] must be thrownBy {
        withSSLHandler(Mode.VerifyFull, "127.0.0.1") { handler => }
      }
    }

  }

}
