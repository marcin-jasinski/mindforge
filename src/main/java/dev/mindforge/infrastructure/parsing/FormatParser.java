package dev.mindforge.infrastructure.parsing;

import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;

/** Parses one document format. A new format is a new implementation registered with {@link ParserRegistry}. */
public interface FormatParser {

    /** @throws UploadRejectedException when the bytes cannot be read as this format */
    ParsedDocument parse(byte[] content);
}
