package dev.mindforge.unit.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.infrastructure.parsing.MarkdownParser;

class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser();

    @Test
    void shouldReadFrontmatterHeadingsProseAndCodeInDocumentOrder() {
        String markdown = """
            ---
            lesson_id: bio-3
            title: "Mitoza komórkowa"
            author: 'Anna'
            ---
            # Mitoza

            Mitoza to **podział** komórki.

            ## Fazy

            - profaza
            - metafaza

            ```python
            # not a heading
            print("x")
            ```
            """;

        ParsedDocument parsed = parser.parse(markdown.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.metadata())
            .isEqualTo(Map.of("lesson_id", "bio-3", "title", "Mitoza komórkowa", "author", "Anna"));
        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.heading("Mitoza", 1, 0),
            ContentBlock.text("Mitoza to **podział** komórki.", 1),
            ContentBlock.heading("Fazy", 2, 2),
            ContentBlock.text("- profaza\n- metafaza", 3),
            ContentBlock.code("# not a heading\nprint(\"x\")", 4));
        assertThat(parsed.text()).isEqualTo(markdown);
    }

    @Test
    void shouldHaveNoMetadataWithoutFrontmatter() {
        ParsedDocument parsed = parser.parse("Samo zdanie.".getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.metadata()).isEmpty();
        assertThat(parsed.blocks()).containsExactly(ContentBlock.text("Samo zdanie.", 0));
    }
}
