package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.FetchedArticle;

class FetchedArticleTest {

    @Test
    void blocks_areCopiedSoALaterChangeToTheSourceListIsNotSeen() {
        List<ContentBlock> blocks = new ArrayList<>(List.of(ContentBlock.text("Mitosis", 0)));

        FetchedArticle article = makeArticle(blocks);
        blocks.add(ContentBlock.text("Meiosis", 1));

        assertThat(article.blocks()).extracting(ContentBlock::content).containsExactly("Mitosis");
    }

    @Test
    void title_mayBeNull() {
        FetchedArticle article = new FetchedArticle(URI.create("https://example.org/a"), null, "text",
            List.of(ContentBlock.text("text", 0)));

        assertThat(article.title()).isNull();
    }

    private static FetchedArticle makeArticle(List<ContentBlock> blocks) {
        return new FetchedArticle(URI.create("https://example.org/a"), "Mitosis", "Mitosis", blocks);
    }
}
