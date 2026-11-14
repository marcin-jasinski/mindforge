package dev.mindforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

import dev.mindforge.infrastructure.security.EgressDeniedException.Reason;

class TestEgressPoliciesTest {

    private static final String HOST = "articles.test";
    private static final int PORT = 18080;

    @Test
    void shouldReturnTheAddressesWhenEveryOneIsPublic() throws UnknownHostException {
        InetAddress first = InetAddress.getByName("93.184.215.14");
        InetAddress second = InetAddress.getByName("2606:4700::1");
        EgressPolicy policy = makePolicy(first, second);

        assertThat(policy.resolve(HOST)).containsExactly(first, second);
    }

    @Test
    void shouldRefuseAHostWhenAnyAddressIsNotPublic() throws UnknownHostException {
        EgressPolicy policy = makePolicy(InetAddress.getByName("93.184.215.14"), InetAddress.getByName("10.0.0.1"));

        assertRefused(() -> policy.resolve(HOST), Reason.ADDRESS);
    }

    @Test
    void shouldRefuseAHostTheResolverCannotResolve() {
        EgressPolicy failing = new EgressPolicy(name -> {
            throw new UncheckedIOException(new UnknownHostException(name));
        }, EgressPolicy::isPublicUnicast, port -> true);
        EgressPolicy empty = new EgressPolicy(name -> new InetAddress[0], EgressPolicy::isPublicUnicast, port -> true);

        assertRefused(() -> failing.resolve(HOST), Reason.UNRESOLVABLE);
        assertRefused(() -> empty.resolve(HOST), Reason.UNRESOLVABLE);
        assertRefused(() -> failing.resolve(" "), Reason.HOST);
    }

    @Test
    void shouldAdmitOnlyTheMappedHostAddressAndPort() {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        EgressPolicy policy = TestEgressPolicies.admitting(HOST, loopback, PORT);

        String url = "http://" + HOST + ":" + PORT + "/a";

        assertThat(policy.check(url)).isEqualTo(URI.create(url));
        assertThat(policy.resolve(HOST)).containsExactly(loopback);
        assertRefused(() -> policy.check("http://" + HOST + ":" + (PORT + 1) + "/a"), Reason.PORT);
        assertRefused(() -> policy.check("http://127.0.0.2:" + PORT + "/a"), Reason.ADDRESS);
        assertRefused(() -> policy.check("http://169.254.169.254/"), Reason.ADDRESS);
        assertRefused(() -> policy.resolve("other.test"), Reason.UNRESOLVABLE);
    }

    @Test
    void shouldRefuseTheMappedLoopbackAddressUnderStrictAddresses() {
        EgressPolicy policy = TestEgressPolicies.strictAddresses(HOST, InetAddress.getLoopbackAddress(), PORT);

        assertThat(policy.check("http://" + HOST + ":" + PORT + "/a")).isNotNull();
        assertRefused(() -> policy.resolve(HOST), Reason.ADDRESS);
        assertRefused(() -> policy.check("http://127.0.0.1:" + PORT + "/a"), Reason.ADDRESS);
    }

    private static EgressPolicy makePolicy(InetAddress... answer) {
        return new EgressPolicy(name -> answer, EgressPolicy::isPublicUnicast, port -> true);
    }

    private static void assertRefused(Runnable call, Reason reason) {
        assertThatExceptionOfType(EgressDeniedException.class)
            .isThrownBy(call::run)
            .satisfies(e -> assertThat(e.reason()).isEqualTo(reason));
    }
}
