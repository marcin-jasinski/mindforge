package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.ingest.ImageContext;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ImageInput;

class ImageContextTest {

    @Test
    void before_joinsThePrecedingHeadingsAndTextsInDocumentOrder() {
        List<ContentBlock> blocks = List.of(
            ContentBlock.heading("Mitosis", 1, 0),
            ContentBlock.text("Mitosis is cell division.", 1),
            ContentBlock.code("int x;", 2),
            makeImage(4),
            ContentBlock.text("After the image.", 5));

        assertThat(ImageContext.before(blocks, 3)).isEqualTo("Mitosis\n\nMitosis is cell division.");
    }

    @Test
    void before_isEmptyForAFirstImage() {
        assertThat(ImageContext.before(List.of(makeImage(0), ContentBlock.text("Later.", 1)), 0)).isEmpty();
    }

    @Test
    void before_skipsEarlierImagesSoNoDescriptionEntersTheContext() {
        List<ContentBlock> blocks = List.of(ContentBlock.text("Intro.", 0), makeImage(1), makeImage(2));

        assertThat(ImageContext.before(blocks, 2)).isEqualTo("Intro.");
    }

    @Test
    void before_keepsTheLastCharactersUpToTheCap() {
        String older = "a".repeat(1000);
        String newer = "b".repeat(1000);
        List<ContentBlock> blocks = List.of(ContentBlock.text("old start", 0), ContentBlock.text(older, 1),
            ContentBlock.text(newer, 2), makeImage(3));

        String context = ImageContext.before(blocks, 3);

        assertThat(context).hasSize(ImageContext.CONTEXT_CHARS)
            .isEqualTo("a".repeat(ImageContext.CONTEXT_CHARS - 1002) + "\n\n" + newer);
    }

    private static ContentBlock makeImage(int position) {
        return ContentBlock.image(new ImageInput(new byte[] {1, 2, 3}, "image/png"), position);
    }
}
