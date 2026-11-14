# The article fetch uses Apache HttpClient 5 with a resolver that returns only checked addresses

Status: Accepted (2026-11-14).

The article fetcher uses Apache HttpClient 5 (`httpclient5`, version from the Spring Boot BOM). Its connection
manager has a custom `DnsResolver` that resolves the host through `EgressPolicy` and returns only addresses that are
public unicast. If any address fails, the lookup fails. So the client connects only to an address that was checked,
and there is no second resolution. TLS SNI and hostname verification stay on the host name. The client does not
follow redirects. The fetcher follows 301, 302, 303, 307 and 308 in code, checks each target with `EgressPolicy`
again, and stops after 5 hops. `EgressPolicy.check` validates IP-literal hosts, because they are not resolved. The
client does not read system proxy properties. Other Spring HTTP clients stay on the JDK transport through
`spring.http.clients.imperative.factory: jdk`.

## Considered Options

- **JDK `HttpClient`, connect to the checked IP literal**: needs the JVM-wide restricted `Host` header flag and a
  JVM-wide hostname-verification disable. This weakens TLS for every outbound call.
- **JDK `HttpClient`, check and then connect by name**: the client resolves the name a second time. Only the JVM DNS
  cache narrows the rebinding window, an operator can change that cache, and a test cannot prove it. ADR 0022 forbids
  a second resolution.
- **Treat any 3xx as a failed fetch**: most article links redirect (http to https, short links).
- **OkHttp with a custom `Dns`**: no new jar, but the application ships `okhttp-4.12.0` and `okhttp-jvm-5.3.2`, both
  with package `okhttp3`. Jar order would select the class that runs the SSRF control.
- **A JVM-wide `InetAddressResolverProvider`**: it also resolves the datasource, OTLP and the dev model provider on
  `localhost`.

## Consequences

- One new runtime dependency, recorded in `tech-stack.md`. Its version follows the Boot BOM.
- If the request-factory pin is removed, Spring's auto-detection moves other `RestClient` users to HttpClient 5.
- The test resolver seam of ADR 0022 is also the path the client connects through, so a test can show that a name
  that resolves to loopback is refused at connect.
- The guard test of ADR 0023 counts `org.apache.hc.client5` as an HTTP-client marker.

Decided in [D04](../factory/phase-17/decisions/D04-dns-rebinding-and-redirects.md) by the dark-factory run
`phase-17`.
