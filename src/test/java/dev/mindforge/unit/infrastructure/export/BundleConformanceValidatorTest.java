package dev.mindforge.unit.infrastructure.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.application.wiki.LogRenderer;
import dev.mindforge.infrastructure.export.BundleConformanceValidator;

class BundleConformanceValidatorTest {

    private static final String PAGE = "---\ntype: Concept\ntitle: 'Mitoza: \"podział\"'\n---\n# Faza\n";

    @Test
    void shouldAcceptTheBundleOfAnEmptyKnowledgeBaseAndAWellFormedPage() {
        Map<String, String> files = makeFiles();
        files.put("concepts/mitoza.md", PAGE);
        files.put("index.md", "---\nokf_version: \"0.1\"\n---\n# Concepts\n\n* [Mitoza \\[x\\]](/concepts/mitoza.md) - Opis.\n\n# Sources\n");
        files.put("log.md", "# Update Log\n\n## 2026-09-10\n* **Ingest**: [bio](/sources/bio.md) — 1 created.\n");

        assertThat(BundleConformanceValidator.violations(makeFiles())).isEmpty();
        assertThat(BundleConformanceValidator.violations(files)).isEmpty();
    }

    @Test
    void shouldRejectAPageWithoutParseableFrontmatterOrType() {
        Map<String, String> files = makeFiles();
        files.put("concepts/a.md", "# Faza\n");
        files.put("concepts/b.md", "---\ntitle: [unclosed\n---\n");
        files.put("concepts/c.md", "---\ntype: ''\n---\n");

        assertThat(BundleConformanceValidator.violations(files)).containsExactly(
            "concepts/a.md: no parseable YAML frontmatter", "concepts/b.md: no parseable YAML frontmatter",
            "concepts/c.md: frontmatter has no type");
    }

    @Test
    void shouldRejectAPathOutsideTheGrammarOrOnAReservedName() {
        Map<String, String> files = makeFiles();
        for (String path : List.of("concepts/index.md", "concepts/Mitoza.md", "notes/mitoza.md", "concepts/a--b.md")) {
            files.put(path, PAGE);
        }

        assertThat(BundleConformanceValidator.violations(files)).hasSize(4)
            .allMatch(violation -> violation.endsWith("not a (concepts|sources)/<identifier>.md path"));
    }

    @Test
    void shouldRejectAnIndexWithExtraFrontmatterOrALineThatIsNotAnEntry() {
        Map<String, String> extraKey = makeFiles();
        extraKey.put("index.md", "---\nokf_version: \"0.1\"\ntitle: x\n---\n# Concepts\n");
        Map<String, String> strayLine = makeFiles();
        strayLine.put("index.md", "# Concepts\n\nMitoza\n* [a](/concepts/a.md) - ok\n");
        Map<String, String> entryBeforeSection = makeFiles();
        entryBeforeSection.put("index.md", "* [a](/concepts/a.md)\n# Concepts\n");

        assertThat(BundleConformanceValidator.violations(extraKey)).containsExactly(
            "index.md: frontmatter may hold only okf_version");
        assertThat(BundleConformanceValidator.violations(strayLine)).containsExactly("index.md: unexpected line 'Mitoza'");
        assertThat(BundleConformanceValidator.violations(entryBeforeSection)).hasSize(1);
    }

    @Test
    void shouldRejectALogDateWithoutEntriesOrASecondHeading() {
        Map<String, String> emptyDay = makeFiles();
        emptyDay.put("log.md", "# Update Log\n\n## 2026-09-10\n\n## 2026-09-09\n* x\n");
        Map<String, String> secondHeading = makeFiles();
        secondHeading.put("log.md", "# Update Log\n# Again\n");

        assertThat(BundleConformanceValidator.violations(emptyDay)).containsExactly(
            "log.md: a date heading without entries");
        assertThat(BundleConformanceValidator.violations(secondHeading)).containsExactly(
            "log.md: unexpected line '# Again'");
    }

    private static Map<String, String> makeFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("index.md", IndexRenderer.render(List.of()));
        files.put("log.md", LogRenderer.render(List.of()));
        return files;
    }
}
