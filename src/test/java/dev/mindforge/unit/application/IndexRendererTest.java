package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.PageType;

class IndexRendererTest {

    private static final String FRONTMATTER = "---\nokf_version: \"0.1\"\n---\n";

    @Test
    void shouldAlwaysRenderBothSectionsEvenWhenEmpty() {
        assertThat(IndexRenderer.render(List.of())).isEqualTo(FRONTMATTER + "# Concepts\n\n# Sources\n");
    }

    @Test
    void shouldListEachPageUnderItsTypeWithItsTitleEscaped() {
        String index = IndexRenderer.render(List.of(
            makeEntry("sources/biology-lesson-3", "Biology — lesson 3", PageType.SOURCE_SUMMARY),
            makeEntry("concepts/table-a-b", "Table [a]\\b", PageType.CONCEPT)));

        assertThat(index).isEqualTo(FRONTMATTER
            + "# Concepts\n\n"
            + "* [Table \\[a\\]\\\\b](/concepts/table-a-b.md) - Description.\n\n"
            + "# Sources\n\n"
            + "* [Biology — lesson 3](/sources/biology-lesson-3.md) - Description.\n");
    }

    @Test
    void shouldSortByPolishCollationThenByPath() {
        String index = IndexRenderer.render(List.of(
            makeEntry("concepts/zaba", "Żaba", PageType.CONCEPT),
            makeEntry("concepts/zebra", "Zebra", PageType.CONCEPT),
            makeEntry("concepts/mitosis-b", "Mitosis", PageType.CONCEPT),
            makeEntry("concepts/lodz", "Łódź", PageType.CONCEPT),
            makeEntry("concepts/mitosis-a", "Mitosis", PageType.CONCEPT),
            makeEntry("concepts/lublin", "Lublin", PageType.CONCEPT)));

        assertThat(index.lines().filter(line -> line.startsWith("* ")))
            .extracting(line -> line.substring(line.indexOf("](/") + 3, line.indexOf(".md)")))
            .containsExactly("concepts/lublin", "concepts/lodz", "concepts/mitosis-a", "concepts/mitosis-b",
                "concepts/zebra", "concepts/zaba");
    }

    private static IndexEntry makeEntry(String path, String title, PageType type) {
        return new IndexEntry(path, title, "Description.", type);
    }
}
