# Connecting securely (TLS and authentication)

Helenus is a thin layer over the Apache Cassandra Java driver, so how you connect,
including TLS/mTLS and authentication, is configured on the driver, not in Helenus.
This guide shows the common cases and points at the driver reference. Once the
session is built you use it with Helenus exactly as elsewhere: `implicit val session: CqlSession = ...`.

> The contact-point examples in this repository and its tests target an
> unauthenticated `localhost` node for development only. Do not model a production
> connection on them. Always enable TLS and authentication against a real cluster.

## Where configuration lives

The driver reads `application.conf` (HOCON) from the classpath under the
`datastax-java-driver` key, layered over its own `reference.conf`. You can also
build the configuration programmatically. Nothing below is Helenus-specific; it is
the standard DataStax driver configuration.

## Authentication

Cassandra's built-in authenticator uses username and password. Configure the
driver's auth provider, and source the credentials from the environment or a
secret manager. Never commit real credentials to `application.conf`.

```hocon
datastax-java-driver {
  advanced.auth-provider {
    class = PlainTextAuthProvider
    username = ${CASSANDRA_USERNAME}
    password = ${CASSANDRA_PASSWORD}
  }
}
```

Or build the session in code, reading the credentials from the environment:

```scala
import com.datastax.oss.driver.api.core.CqlSession

val session: CqlSession =
  CqlSession.builder()
    .withAuthCredentials(sys.env("CASSANDRA_USERNAME"), sys.env("CASSANDRA_PASSWORD"))
    .build()
```

## TLS (encryption in transit)

Enable the driver's SSL engine factory and point it at a truststore that trusts
the cluster's certificate authority. Keep `hostname-validation = true` in
production so a valid certificate for the wrong host is rejected.

```hocon
datastax-java-driver {
  advanced.ssl-engine-factory {
    class = DefaultSslEngineFactory
    truststore-path = "/etc/cassandra/certs/truststore.jks"
    truststore-password = ${CASSANDRA_TRUSTSTORE_PASSWORD}
    hostname-validation = true
  }
}
```

## Mutual TLS (client certificates)

When the cluster requires client certificates, also configure a keystore holding
the client key and certificate:

```hocon
datastax-java-driver {
  advanced.ssl-engine-factory {
    class = DefaultSslEngineFactory
    truststore-path = "/etc/cassandra/certs/truststore.jks"
    truststore-password = ${CASSANDRA_TRUSTSTORE_PASSWORD}
    keystore-path = "/etc/cassandra/certs/client-keystore.jks"
    keystore-password = ${CASSANDRA_KEYSTORE_PASSWORD}
    hostname-validation = true
  }
}
```

For advanced cases (a custom `SSLContext`, in-memory key material, or a custom
`AuthProvider`), build the `CqlSession` programmatically and pass a
`ProgrammaticSslEngineFactory` or an `AuthProvider` instance to the builder.

## Handling secrets

- Keep passwords and keystore passwords out of source control. Use environment
  variable substitution (as above) or a secret manager.
- Restrict filesystem permissions on truststore and keystore files.
- Helenus never logs bound parameter values; it logs only statement text (at
  debug, and on an execution error). Keep sensitive data in bound parameters,
  never spliced into the query string, so it does not reach logs. See the
  [Statement Options](options.md) guide for the execution surface.

## Reference

- DataStax Java driver SSL configuration and authentication documentation.
- The `datastax-java-driver` `reference.conf` shipped with `java-driver-core`
  lists every option with defaults.
