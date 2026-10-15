package dev.mindforge.infrastructure.parsing;

import java.util.Map;
import java.util.Set;

import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.port.DocumentParser;

/**
 * Dispatches a document to the parser registered for its MIME type. Formats are registered where the
 * registry is built, in configuration; adding one never changes this class.
 */
public class ParserRegistry implements DocumentParser {

    private final Map<String, FormatParser> parsers;

    public ParserRegistry(Map<String, FormatParser> parsers) {
        this.parsers = Map.copyOf(parsers);
    }

    /** The MIME types a parser is registered for — the upload allowlist. */
    public Set<String> mimeTypes() {
        return parsers.keySet();
    }

    @Override
    public ParsedDocument parse(String mimeType, byte[] content) {
        FormatParser parser = mimeType == null ? null : parsers.get(mimeType);
        if (parser == null) {
            throw new UploadRejectedException("Unsupported document type: " + mimeType);
        }
        return parser.parse(content);
    }
}
