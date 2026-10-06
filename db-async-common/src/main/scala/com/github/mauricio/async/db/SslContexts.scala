package com.github.mauricio.async.db

import java.io.File

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContext, SslContextBuilder}

/**
 * Helpers to build the Netty [[SslContext]] instances accepted by
 * [[Configuration.ssl]]. Passing `None` disables SSL; passing a context enables
 * SSL, and mutual TLS is enabled by giving the context client credentials (see
 * [[SslContexts.clientSslContext]]).
 */
object SslContexts {

  /**
   * A Netty [[SslContext]] that trusts [[rootCert]] (or the JVM default trust
   * store when None) and presents the supplied client certificate/key pair.
   */
  def clientSslContext(
    rootCert: Option[File],
    clientCert: File,
    clientKey: File,
    clientKeyPassword: Option[String] = None,
    verifyHostname: Boolean = false
  ): SslContext = {
    val builder =
      SslContextBuilder
        .forClient()
        .endpointIdentificationAlgorithm(
          if (verifyHostname) "HTTPS" else null
        )
    rootCert match {
      case Some(cert) => builder.trustManager(cert)
      case None       => builder
    }
    builder.keyManager(clientCert, clientKey, clientKeyPassword.orNull).build()
  }

  /**
   * A Netty [[SslContext]] trusting [[rootCert]] (or the JVM default trust
   * store when None) without client credentials.
   */
  def trustedSslContext(
    rootCert: Option[File],
    verifyHostname: Boolean = false
  ): SslContext = {
    val builder =
      SslContextBuilder
        .forClient()
        .endpointIdentificationAlgorithm(
          if (verifyHostname) "HTTPS" else null
        )
    rootCert match {
      case Some(cert) => builder.trustManager(cert)
      case None       => builder
    }
    builder.build()
  }

  /**
   * A Netty [[SslContext]] that does not verify the server certificate.
   */
  def insecureSslContext: SslContext =
    SslContextBuilder
      .forClient()
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .build()

  /**
   * Derives an [[SslContext]] from classic `sslmode`/`sslrootcert` style
   * connection properties. Returns None when SSL is disabled.
   */
  def fromProperties(properties: Map[String, String]): Option[SslContext] = {
    properties.getOrElse("sslmode", "disable") match {
      case "require" | "prefer" => Some(insecureSslContext)
      case "verify-ca" =>
        Some(trustedSslContext(properties.get("sslrootcert").map(new File(_))))
      case "verify-full" =>
        Some(
          trustedSslContext(
            properties.get("sslrootcert").map(new File(_)),
            verifyHostname = true
          )
        )
      case _ => None
    }
  }
}
