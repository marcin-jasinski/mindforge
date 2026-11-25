package dev.mindforge.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tripwire for T26 / D12: only {@code MarkdownStructure} matches markdown heading or link syntax with a regex. Scans
 * the string literals passed to {@code Pattern.compile} in {@code src/main/java}.
 */
class MarkdownRegexGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String OWNER = "src/main/java/dev/mindforge/domain/model/MarkdownStructure.java";

    /** Files allowed by exact path outside the owner. Each entry needs a reason, and a stale entry fails. */
    private static final Map<String, String> ALLOWED = Map.of(
        "src/main/java/dev/mindforge/infrastructure/export/BundleConformanceValidator.java",
        "validates the generated index.md/log.md OKF bundle grammar; an independent oracle, not wiki page syntax");

    private static final String COMPILE_CALL = "Pattern.compile(";
    /** A literal that starts with a heading marker, optionally after inline flags, an anchor or a group. */
    private static final Pattern LEADING_HEADING = Pattern.compile("^(?:\\(\\?[a-zA-Z]+\\))?\\^?\\(?#");
    /**
     * {@code ]\\(} as written in Java source: a closing link-text bracket (escaped or bare) followed by an escaped
     * paren. A raw {@code ](} (a character class then a group) is not link syntax.
     */
    private static final String ESCAPED_LINK = "]\\\\(";

    @Test
    void shouldFindMarkdownRegexesOnlyInTheOwnerAndAllowedFiles() {
        assertThat(offenders(Path.of("")))
            .as("Pattern.compile with markdown heading or link syntax outside %s (use MarkdownStructure, or allow "
                + "the file by exact path with a reason)", OWNER)
            .isEmpty();
    }

    @Test
    void shouldStillFindAHitInEveryAllowedFile() {
        assertThat(filesWithHits(Path.of("")))
            .as("stale ALLOWED entry: the file no longer has a markdown regex; remove it from the allowlist")
            .containsAll(ALLOWED.keySet());
    }

    @Test
    void shouldReportAPlantedOffenderAndIgnoreTheOwnerAndAllowedCopies(@TempDir Path root) throws IOException {
        write(root, "src/main/java/dev/mindforge/application/Headings.java",
            "class Headings { Object p = Pattern.compile(\"^## (.+)$\"); }");
        write(root, "src/main/java/dev/mindforge/agent/Links.java",
            "class Links { Object p = Pattern.compile(\n    \"\\\\[(.+)\\\\]\\\\((.+)\\\\)\"); }");
        write(root, "src/main/java/dev/mindforge/agent/BareLinks.java",
            "class BareLinks { Object p = Pattern.compile(\"\\\\[(.+?)]\\\\((.+?)\\\\)\"); }");
        write(root, OWNER, "class MarkdownStructure { Object p = Pattern.compile(\"(#{1,6}) (.*)\"); }");
        write(root, ALLOWED.keySet().iterator().next(),
            "class BundleConformanceValidator { Object p = Pattern.compile(\"## \\\\d{4}\"); }");

        assertThat(offenders(root)).containsExactly("src/main/java/dev/mindforge/agent/BareLinks.java",
            "src/main/java/dev/mindforge/agent/Links.java",
            "src/main/java/dev/mindforge/application/Headings.java");
    }

    @Test
    void shouldNotReportNonMarkdownPatterns(@TempDir Path root) throws IOException {
        write(root, "src/main/java/dev/mindforge/domain/Identifier.java",
            "class Identifier { Object p = Pattern.compile(\"[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){0,\" + (MAX - 1) "
                + "+ \"}\"); }");
        write(root, "src/main/java/dev/mindforge/infrastructure/DocxParser.java",
            "class DocxParser { Object p = Pattern.compile(\"heading ([1-9])\", Pattern.CASE_INSENSITIVE); }");
        write(root, "src/main/java/dev/mindforge/application/ArticleFetcher.java",
            "class ArticleFetcher { Object p = Pattern.compile(\"(?i)https?://[^\\\\s<>\\\\[\\\\]\\\"'`]+\"); }");

        assertThat(filesWithHits(root)).isEmpty();
    }

    private static List<String> offenders(Path root) {
        return filesWithHits(root).stream()
            .filter(relative -> !relative.equals(OWNER) && !ALLOWED.containsKey(relative))
            .toList();
    }

    private static List<String> filesWithHits(Path root) {
        try (Stream<Path> files = Files.walk(root.resolve(MAIN_SOURCES))) {
            return files.filter(path -> path.toString().endsWith(".java"))
                .filter(MarkdownRegexGuardTest::hasMarkdownRegex)
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean hasMarkdownRegex(Path file) {
        try {
            String source = Files.readString(file);
            for (int call = source.indexOf(COMPILE_CALL); call >= 0; call = source.indexOf(COMPILE_CALL, call + 1)) {
                List<String> literals = literalsOfCall(source, call + COMPILE_CALL.length());
                if (literals.stream().anyMatch(literal -> LEADING_HEADING.matcher(literal).find())
                    || isMarkdown(String.join("", literals))) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isMarkdown(String literal) {
        return literal.contains("#{") || literal.contains("## ") || literal.contains(ESCAPED_LINK);
    }

    /** The string literals, as written in source, between the call's opening parenthesis and its matching close. */
    private static List<String> literalsOfCall(String source, int start) {
        List<String> literals = new ArrayList<>();
        int depth = 1;
        int i = start;
        while (i < source.length() && depth > 0) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                int end = closingQuote(source, i);
                if (c == '"') {
                    literals.add(source.substring(i + 1, end));
                }
                i = end + 1;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            i++;
        }
        return literals;
    }

    private static int closingQuote(String source, int open) {
        char quote = source.charAt(open);
        int i = open + 1;
        while (i < source.length() && source.charAt(i) != quote) {
            i += source.charAt(i) == '\\' ? 2 : 1;
        }
        return i;
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
