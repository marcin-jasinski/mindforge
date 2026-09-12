package dev.mindforge.domain.port;

import dev.mindforge.domain.model.UploadRejectedException;

/** Decides whether an untrusted upload may be ingested at all, before any byte of it is parsed. */
public interface UploadPolicy {

    /**
     * Returns the filename to store for an admitted upload.
     *
     * @throws UploadRejectedException for a refused type, an oversized upload or an unsafe filename
     */
    String admit(String filename, String mimeType, long sizeBytes);
}
