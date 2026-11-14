package dev.mindforge.infrastructure.web;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import org.apache.hc.client5.http.DnsResolver;

import dev.mindforge.infrastructure.security.EgressPolicy;

/**
 * Resolves every host the article client connects to through {@link EgressPolicy#resolve}, so the client connects
 * to exactly the checked public addresses (ADR 0025). A refused host throws the policy's
 * {@code EgressDeniedException}; TLS SNI and hostname verification still use the name.
 */
class CheckedDnsResolver implements DnsResolver {

    private final EgressPolicy egressPolicy;

    CheckedDnsResolver(EgressPolicy egressPolicy) {
        this.egressPolicy = egressPolicy;
    }

    @Override
    public InetAddress[] resolve(String host) {
        return egressPolicy.resolve(host).toArray(InetAddress[]::new);
    }

    @Override
    public List<InetSocketAddress> resolve(String host, int port) {
        return egressPolicy.resolve(host).stream().map(address -> new InetSocketAddress(address, port)).toList();
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }
}
