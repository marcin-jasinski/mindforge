package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ImageInput;

class ImageInputTest {

    @Test
    void rejectsNullData() {
        assertThatNullPointerException().isThrownBy(() -> new ImageInput(null, "image/png"));
    }

    @Test
    void rejectsNullMimeType() {
        assertThatNullPointerException().isThrownBy(() -> new ImageInput(makeBytes(), null));
    }

    @Test
    void rejectsEmptyData() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new ImageInput(new byte[0], "image/png"));
    }

    @Test
    void rejectsAMimeTypeThatIsNotAnImage() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new ImageInput(makeBytes(), "application/pdf"));
    }

    @Test
    void mutatingTheInputArray_doesNotChangeTheRecord() {
        byte[] bytes = makeBytes();
        ImageInput image = new ImageInput(bytes, "image/png");

        bytes[0] = 99;

        assertThat(image.data()).containsExactly(1, 2, 3);
    }

    @Test
    void mutatingTheReturnedArray_doesNotChangeTheRecord() {
        ImageInput image = makeImage();

        image.data()[0] = 99;

        assertThat(image.data()).containsExactly(1, 2, 3);
    }

    @Test
    void comparesByContent() {
        assertThat(makeImage()).isEqualTo(makeImage()).hasSameHashCodeAs(makeImage());
        assertThat(makeImage()).isNotEqualTo(new ImageInput(makeBytes(), "image/jpeg"));
        assertThat(makeImage()).isNotEqualTo(new ImageInput(new byte[] {1, 2, 4}, "image/png"));
    }

    @Test
    void toString_showsMimeTypeAndLengthOnly() {
        assertThat(makeImage().toString()).isEqualTo("ImageInput[mimeType=image/png, bytes=3]");
    }

    private static byte[] makeBytes() {
        return new byte[] {1, 2, 3};
    }

    private static ImageInput makeImage() {
        return new ImageInput(makeBytes(), "image/png");
    }
}
