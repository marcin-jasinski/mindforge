package dev.mindforge.domain.model;

import java.util.List;

/** What Extract read from one chunk: its claims, and a one-paragraph digest a long document's summary is written from. */
public record ExtractResult(List<Claim> claims, String chunkDigest) {

    public ExtractResult {
        claims = List.copyOf(claims);
    }
}
