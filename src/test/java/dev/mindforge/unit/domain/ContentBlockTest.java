package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ImageInput;

class ContentBlockTest {

    @Test
    void anImageBlockWithoutAnImage_isRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new ContentBlock(BlockType.IMAGE, "", null, null, null, 0, null));
    }

    @Test
    void aTextBlockWithAnImage_isRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new ContentBlock(BlockType.TEXT, "text", null, null, null, 0, makeImage()));
    }

    @Test
    void imageFactory_buildsAnImageBlockWithEmptyContent() {
        ContentBlock block = ContentBlock.image(makeImage(), 4);

        assertThat(block.blockType()).isEqualTo(BlockType.IMAGE);
        assertThat(block.content()).isEmpty();
        assertThat(block.mediaRef()).isNull();
        assertThat(block.mediaType()).isNull();
        assertThat(block.position()).isEqualTo(4);
        assertThat(block.image()).isEqualTo(makeImage());
    }

    @Test
    void textFactories_carryNoImage() {
        assertThat(ContentBlock.heading("Title", 1, 0).image()).isNull();
        assertThat(ContentBlock.text("Body", 1).image()).isNull();
        assertThat(ContentBlock.code("x = 1", 2).image()).isNull();
    }

    @Test
    void twoImageBlocksWithEqualBytes_areEqual() {
        assertThat(ContentBlock.image(makeImage(), 0)).isEqualTo(ContentBlock.image(makeImage(), 0));
    }

    @Test
    void toStringOfAnImageBlock_hasNoBytes() {
        byte[] bytes = makeBytes();

        String text = ContentBlock.image(new ImageInput(bytes, "image/png"), 0).toString();

        assertThat(text)
            .contains("mimeType=image/png", "bytes=" + bytes.length)
            .doesNotContain(Base64.getEncoder().encodeToString(bytes));
    }

    private static byte[] makeBytes() {
        return "png-bytes-for-a-test-image".getBytes();
    }

    private static ImageInput makeImage() {
        return new ImageInput(makeBytes(), "image/png");
    }
}
