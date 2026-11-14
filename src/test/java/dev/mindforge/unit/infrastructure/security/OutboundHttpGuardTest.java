package dev.mindforge.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tripwire for D03 / ADR 0023: outbound HTTP to content- or user-derived URLs goes through {@code EgressPolicy}, so
 * HTTP-client and HTML-fetch code may live only in the article fetch adapter ({@code dev.mindforge.infrastructure.web}).
 */
class OutboundHttpGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String ARTICLE_ADAPTER_DIR = "dev/mindforge/infrastructure/web/";
    private static final Path ARTICLE_SOURCE = MAIN_SOURCES.resolve(ARTICLE_ADAPTER_DIR + "HttpArticleSource.java");

    private static final List<String> MARKERS = List.of("java.net.http.HttpClient", "HttpURLConnection",
        ".openConnection(", ".openStream(", "RestClient", "RestTemplate", "WebClient", "Jsoup.connect",
        "org.apache.hc.client5", "org.jsoup");

    /**
     * Files allowed by exact path outside the article fetch adapter. Each one calls an operator-configured endpoint,
     * which is trusted configuration and not checked by EgressPolicy (ADR 0023).
     */
    private static final Set<String> OPERATOR_ENDPOINT_FILES = Set.of(
        // OAuth2 token endpoint of the operator-configured provider, called through a JDK-transport RestClient.
        "dev/mindforge/api/config/SecurityConfig.java");

    private static final Pattern EGRESS_POLICY_FIELD = Pattern.compile("\\bEgressPolicy\\s+(\\w+)\\s*;");

    @Test
    void shouldFindHttpClientCodeOnlyInTheArticleFetchAdapter() {
        assertThat(offenders(MAIN_SOURCES))
            .as("HTTP-client or HTML-fetch code outside %s (route it through EgressPolicy in the article fetch "
                + "adapter, or allow an operator-configured endpoint by exact path per ADR 0023)",
                ARTICLE_ADAPTER_DIR)
            .isEmpty();
    }

    @Test
    void shouldHaveTheArticleSourceCallEgressPolicyCheck() throws IOException {
        assertThat(callsEgressCheck(Files.readString(ARTICLE_SOURCE)))
            .as("%s must call check( on its EgressPolicy field", ARTICLE_SOURCE)
            .isTrue();
    }

    @Test
    void shouldReportAFileWithAMarkerOutsideTheAdapterAndIgnoreTheAdapterItself(@TempDir Path root)
        throws IOException {
        write(root, "dev/mindforge/application/Fetcher.java", "class Fetcher { RestTemplate template; }");
        write(root, "dev/mindforge/application/Clean.java", "class Clean {}");
        write(root, ARTICLE_ADAPTER_DIR + "Adapter.java", "class Adapter { Object d = Jsoup.connect(\"x\"); }");

        assertThat(offenders(root)).containsExactly("dev/mindforge/application/Fetcher.java");
    }

    @Test
    void shouldNotAcceptAnArticleSourceThatNeverCallsCheckOnItsEgressPolicy() {
        assertThat(callsEgressCheck("class A { private final EgressPolicy egressPolicy; void f() { get(); } }"))
            .isFalse();
        assertThat(callsEgressCheck("class A { void f() { other.check(url); } }")).isFalse();
        assertThat(callsEgressCheck("class A { private final EgressPolicy policy; void f() { policy.check(u); } }"))
            .isTrue();
    }

    private static List<String> offenders(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    return !relative.startsWith(ARTICLE_ADAPTER_DIR) && !OPERATOR_ENDPOINT_FILES.contains(relative);
                })
                .filter(OutboundHttpGuardTest::containsMarker)
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean containsMarker(Path file) {
        try {
            String source = Files.readString(file);
            return MARKERS.stream().anyMatch(source::contains);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean callsEgressCheck(String source) {
        Matcher field = EGRESS_POLICY_FIELD.matcher(source);
        while (field.find()) {
            if (source.contains(field.group(1) + ".check(")) {
                return true;
            }
        }
        return false;
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
