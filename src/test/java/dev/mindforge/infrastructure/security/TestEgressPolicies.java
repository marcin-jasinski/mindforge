package dev.mindforge.infrastructure.security;

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Builds relaxed {@link EgressPolicy} instances for tests against a loopback mock server. The
 * resolver knows only the one mapped host; every other host is unresolvable, so no test uses DNS.
 */
public final class TestEgressPolicies {

    private TestEgressPolicies() {
    }

    /** Admits {@code host}, mapped to {@code address}, on the default ports or {@code port}. */
    public static EgressPolicy admitting(String host, InetAddress address, int port) {
        return new EgressPolicy(resolving(host, address), a -> EgressPolicy.isPublicUnicast(a) || a.equals(address),
            ports(port));
    }

    /** Maps {@code host} to {@code address} and admits {@code port}, but admits only public addresses. */
    public static EgressPolicy strictAddresses(String host, InetAddress address, int port) {
        return new EgressPolicy(resolving(host, address), EgressPolicy::isPublicUnicast, ports(port));
    }

    private static Function<String, InetAddress[]> resolving(String host, InetAddress address) {
        return name -> {
            if (name.equals(host)) {
                return new InetAddress[] {address};
            }
            throw new UncheckedIOException(new UnknownHostException(name));
        };
    }

    private static IntPredicate ports(int port) {
        return p -> p == -1 || p == 80 || p == 443 || p == port;
    }
}
