package dev.mindforge.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.mindforge.infrastructure.security.EgressDeniedException;
import dev.mindforge.infrastructure.security.EgressDeniedException.Reason;
import dev.mindforge.infrastructure.security.EgressPolicy;

class EgressPolicyTest {

    private final EgressPolicy policy = new EgressPolicy();

    @Test
    void shouldBeAFinalClassWithOnlyAStrictPublicAndOnePackagePrivateSeamConstructor() {
        assertThat(Modifier.isFinal(EgressPolicy.class.getModifiers())).isTrue();
        Constructor<?>[] constructors = EgressPolicy.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(2);
        assertThat(Arrays.stream(constructors).filter(c -> c.getParameterCount() == 0))
            .singleElement()
            .satisfies(c -> assertThat(Modifier.isPublic(c.getModifiers())).isTrue());
        assertThat(Arrays.stream(constructors).filter(c -> c.getParameterCount() > 0))
            .singleElement()
            .satisfies(c -> {
                assertThat(c.getParameterTypes())
                    .containsExactly(Function.class, Predicate.class, IntPredicate.class);
                int modifiers = c.getModifiers();
                assertThat(Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)
                    || Modifier.isPrivate(modifiers)).isFalse();
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "data:text/plain,hello", "ftp://example.com/a",
        "jar:file:///a.jar!/b", "example.com/article", "//example.com/article"})
    void shouldRefuseANonHttpScheme(String url) {
        assertRefused(url, Reason.SCHEME);
    }

    @Test
    void shouldRefuseANullUrlAsScheme() {
        assertRefused(null, Reason.SCHEME);
    }

    @Test
    void shouldMatchTheSchemeCaseInsensitively() {
        assertThat(policy.check("HTTPS://example.com/a")).isEqualTo(URI.create("HTTPS://example.com/a"));
        assertThat(policy.check("Http://example.com/a")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com:8080/", "https://example.com:8443/", "http://example.com:22/"})
    void shouldRefuseAPortOtherThanTheDefaults(String url) {
        assertRefused(url, Reason.PORT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/", "http://example.com:80/", "https://example.com:443/"})
    void shouldAdmitTheDefaultPorts(String url) {
        assertThat(policy.check(url)).isEqualTo(URI.create(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://user:pw@example.com/", "http://user@example.com/", "http://user@0x7f.1/"})
    void shouldRefuseUserinfo(String url) {
        assertRefused(url, Reason.USERINFO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http:///article", "http:article", "http://my_host.example.com/", "http://exa mple.com/",
        "http://93.184.215.14:8080x/", "http://8.8.8.8:-1/", "http://8.8.8.8:abc/", "http://8.8.8.8:99999999999/",
        "http://93.184.215.14:\uFF18\uFF10\uFF18\uFF10/"})
    void shouldRefuseAUrlWithoutAValidHost(String url) {
        assertRefused(url, Reason.HOST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/", "http://[::1]/", "http://10.0.0.1/", "http://172.16.0.1/",
        "http://172.31.255.255/", "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data/",
        "http://100.64.0.1/", "http://0.0.0.0/", "http://[::]/", "http://[fd00::1]/", "http://[fe80::1]/",
        "http://[::ffff:127.0.0.1]/", "http://[::ffff:10.0.0.1]/", "http://[64:ff9b::7f00:1]/",
        "http://2130706433/", "http://0x7f.1/", "http://0x7f.1:8080/", "http://127.1/", "http://017700000001/",
        "http://127.0.0.1./", "http://224.0.0.1/", "http://[::127.0.0.1]/", "http://[2002:7f00:1::]/"})
    void shouldRefuseANonPublicAddressLiteral(String url) {
        assertRefused(url, Reason.ADDRESS);
    }

    @Test
    void shouldRefuseAPublicAddressWrittenInANonCanonicalForm() {
        assertRefused("http://1572395790/", Reason.HOST);
    }

    @Test
    void shouldAdmitAPublicHostNameWithoutResolvingIt() {
        assertThat(policy.check("https://articles.invalid/a/../b?q=1"))
            .isEqualTo(URI.create("https://articles.invalid/b?q=1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://93.184.215.14/", "http://[2606:4700::1]/"})
    void shouldAdmitAPublicAddressLiteral(String url) {
        assertThat(policy.check(url)).isEqualTo(URI.create(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "0.1.2.3", "10.1.2.3", "100.64.0.1", "100.127.255.255", "127.0.0.1",
        "169.254.169.254", "172.16.0.1", "192.168.0.1", "224.0.0.1", "255.255.255.255", "::", "::1",
        "fc00::1", "fd00::1", "fe80::1", "ff02::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::7f00:1",
        "::7f00:1"})
    void shouldNotTreatASpecialRangeAsPublicUnicast(String literal) throws UnknownHostException {
        assertThat(EgressPolicy.isPublicUnicast(InetAddress.getByName(literal))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.215.14", "8.8.8.8", "100.128.0.1", "172.32.0.1", "2606:4700::1",
        "64:ff9b::808:808"})
    void shouldTreatAPublicAddressAsPublicUnicast(String literal) throws UnknownHostException {
        assertThat(EgressPolicy.isPublicUnicast(InetAddress.getByName(literal))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void shouldUseAFixedMessagePerReason(Reason reason) {
        assertThat(new EgressDeniedException(reason).getMessage()).isNotBlank();
    }

    @Test
    void shouldKeepTheUrlOutOfTheMessage() {
        assertThatExceptionOfType(EgressDeniedException.class)
            .isThrownBy(() -> policy.check("http://secret-token@intranet.local:8080/"))
            .satisfies(e -> assertThat(e.getMessage())
                .isEqualTo(new EgressDeniedException(e.reason()).getMessage())
                .doesNotContain("secret", "intranet", "8080"));
        assertThatExceptionOfType(EgressDeniedException.class)
            .isThrownBy(() -> policy.check("http://10.1.2.3/private"))
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("10.1.2.3", "private"));
    }

    private void assertRefused(String url, Reason reason) {
        assertThatExceptionOfType(EgressDeniedException.class)
            .as(url)
            .isThrownBy(() -> policy.check(url))
            .satisfies(e -> assertThat(e.reason()).isEqualTo(reason));
    }
}
