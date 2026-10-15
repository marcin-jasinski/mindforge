package dev.mindforge.domain.port;

import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;

/** Reads an uploaded document of a given MIME type into blocks and metadata. */
public interface DocumentParser {

    /** @throws UploadRejectedException for a type no parser reads, or bytes that are not of that type */
    ParsedDocument parse(String mimeType, byte[] content);
}
