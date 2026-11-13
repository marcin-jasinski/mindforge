package dev.mindforge.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * The bytes of one image and their MIME type. The only image value in the domain: a document's IMAGE block and a
 * vision call both carry it. The bytes are copied in and out, compared by content, and never printed.
 */
public record ImageInput(byte[] data, String mimeType) {

    public ImageInput {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(mimeType, "mimeType");
        if (data.length == 0) {
            throw new IllegalArgumentException("Image data must not be empty");
        }
        if (!mimeType.startsWith("image/")) {
            throw new IllegalArgumentException("Image MIME type must start with image/: " + mimeType);
        }
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ImageInput that
            && Arrays.equals(data, that.data)
            && mimeType.equals(that.mimeType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(data) + mimeType.hashCode();
    }

    @Override
    public String toString() {
        return "ImageInput[mimeType=" + mimeType + ", bytes=" + data.length + "]";
    }
}
