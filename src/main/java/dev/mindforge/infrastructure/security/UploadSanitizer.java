package dev.mindforge.infrastructure.security;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.port.UploadPolicy;

/**
 * Refuses uploads of a type no parser is registered for, over the size limit, or whose filename
 * reaches outside a single file name; replaces every other unsafe filename character. Letters of
 * any script are kept, because the filename stem can become the lesson title.
 */
public class UploadSanitizer implements UploadPolicy {

    private static final int MAX_FILENAME_LENGTH = 255;
    private static final Pattern UNSAFE_CHARACTER = Pattern.compile("[^\\p{L}\\p{N} ._()-]");
    private static final Pattern DOTS_ONLY = Pattern.compile("\\.+");

    private final long maxBytes;
    private final Set<String> allowedMimeTypes;

    public UploadSanitizer(long maxBytes, Set<String> allowedMimeTypes) {
        this.maxBytes = maxBytes;
        this.allowedMimeTypes = Set.copyOf(allowedMimeTypes);
    }

    @Override
    public String admit(String filename, String mimeType, long sizeBytes) {
        if (filename == null || filename.isBlank()) {
            throw new UploadRejectedException("The upload has no filename");
        }
        String name = filename.strip();
        if (name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0 || DOTS_ONLY.matcher(name).matches()) {
            throw new UploadRejectedException("The filename must be a plain file name, without a path");
        }
        if (mimeType == null || !allowedMimeTypes.contains(mimeType)) {
            throw new UploadRejectedException("Unsupported document type: " + mimeType);
        }
        if (sizeBytes > maxBytes) {
            throw new UploadRejectedException(
                "The upload is " + sizeBytes + " bytes; the limit is " + maxBytes + " bytes");
        }
        String safe = UNSAFE_CHARACTER.matcher(Normalizer.normalize(name, Normalizer.Form.NFC)).replaceAll("_");
        if (safe.codePointCount(0, safe.length()) <= MAX_FILENAME_LENGTH) {
            return safe;
        }
        return safe.substring(0, safe.offsetByCodePoints(0, MAX_FILENAME_LENGTH));
    }
}
