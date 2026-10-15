package dev.mindforge.infrastructure.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import dev.mindforge.domain.model.Identifier;

/**
 * Checks rendered bundle files against OKF §9 (T11, T19) — a pure function over what the renderers produced, and the
 * exporter tests' oracle.
 *
 * <ol>
 *   <li>Every page file has parseable YAML frontmatter (rule 1) with a non-empty {@code type} (rule 2).
 *   <li>{@code index.md} has optional frontmatter holding only {@code okf_version}, then {@code # } sections each
 *       followed by zero or more {@code * [title](url)} lines, optionally ending {@code  - description};
 *       {@code log.md} has one {@code # } heading, then {@code ## YYYY-MM-DD} headings each followed by one or more
 *       {@code * } lines; blank lines anywhere (rule 3).
 *   <li>Every other path is {@code (concepts|sources)/<identifier>.md} with no reserved name (rule 4).
 * </ol>
 */
public final class BundleConformanceValidator {

    private static final Pattern PAGE_FILE =
        Pattern.compile("(concepts|sources)/(" + Identifier.PATTERN.pattern() + ")\\.md");
    private static final Pattern INDEX_ENTRY =
        Pattern.compile("\\* \\[(?:\\\\.|[^\\\\\\]\\n])*\\]\\([^()\\s]+\\)(?: - .*)?");
    private static final Pattern LOG_DATE = Pattern.compile("## \\d{4}-\\d{2}-\\d{2}");
    private static final String FENCE = "---\n";
    private static final String CLOSING_FENCE = "\n---\n";

    private BundleConformanceValidator() {}

    public static List<String> violations(Map<String, String> files) {
        List<String> violations = new ArrayList<>();
        if (!files.containsKey(BundleExporter.INDEX)) {
            violations.add("index.md is missing");
        }
        files.forEach((path, content) -> {
            switch (path) {
                case BundleExporter.INDEX -> checkIndex(content, violations);
                case BundleExporter.LOG -> checkLog(content, violations);
                default -> checkPage(path, content, violations);
            }
        });
        return violations;
    }

    private static void checkPage(String path, String content, List<String> violations) {
        Matcher name = PAGE_FILE.matcher(path);
        if (!name.matches() || Identifier.RESERVED.contains(name.group(2))) {
            violations.add(path + ": not a (concepts|sources)/<identifier>.md path");
        }
        Optional<Map<?, ?>> frontmatter = frontmatter(content);
        if (frontmatter.isEmpty()) {
            violations.add(path + ": no parseable YAML frontmatter");
        } else if (!(frontmatter.get().get("type") instanceof String type) || type.isBlank()) {
            violations.add(path + ": frontmatter has no type");
        }
    }

    private static void checkIndex(String content, List<String> violations) {
        String body = content;
        if (content.startsWith(FENCE)) {
            Optional<Map<?, ?>> frontmatter = frontmatter(content);
            if (frontmatter.isEmpty() || !Set.of("okf_version").containsAll(frontmatter.get().keySet())) {
                violations.add("index.md: frontmatter may hold only okf_version");
                return;
            }
            body = content.substring(content.indexOf(CLOSING_FENCE, FENCE.length() - 1) + CLOSING_FENCE.length());
        }
        boolean inSection = false;
        for (String line : body.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("# ") && line.length() > 2) {
                inSection = true;
            } else if (!inSection || !INDEX_ENTRY.matcher(line).matches()) {
                violations.add("index.md: unexpected line '" + line + "'");
            }
        }
    }

    private static void checkLog(String content, List<String> violations) {
        List<String> lines = content.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty() || !lines.getFirst().startsWith("# ")) {
            violations.add("log.md: must open with one # heading");
            return;
        }
        int entries = -1;
        for (String line : lines.subList(1, lines.size())) {
            if (LOG_DATE.matcher(line).matches()) {
                if (entries == 0) {
                    violations.add("log.md: a date heading without entries");
                }
                entries = 0;
            } else if (line.startsWith("* ") && entries >= 0) {
                entries++;
            } else {
                violations.add("log.md: unexpected line '" + line + "'");
            }
        }
        if (entries == 0) {
            violations.add("log.md: a date heading without entries");
        }
    }

    /** The mapping between a leading {@code ---} line and the next one, if it parses. */
    private static Optional<Map<?, ?>> frontmatter(String content) {
        int end = content.startsWith(FENCE) ? content.indexOf(CLOSING_FENCE, FENCE.length() - 1) : -1;
        if (end < 0) {
            return Optional.empty();
        }
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(content.substring(FENCE.length(), end));
            return parsed instanceof Map<?, ?> map ? Optional.of(map) : Optional.empty();
        } catch (YAMLException e) {
            return Optional.empty();
        }
    }
}
