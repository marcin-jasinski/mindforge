package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.ingest.HeadingChunker;
import dev.mindforge.domain.model.ContentBlock;

class HeadingChunkerTest {

    private static final int MAX_TOKENS = 10;

    @Test
    void shouldKeepAShortDocumentInOneChunk() {
        ContentBlock intro = makeText(6);
        ContentBlock heading = makeHeading("A");
        ContentBlock body = makeText(6);

        assertThat(HeadingChunker.split(List.of(intro, heading, body), MAX_TOKENS))
            .containsExactly(List.of(intro, heading, body));
    }

    @Test
    void shouldStartAChunkAtAHeadingRatherThanSplitTheSectionAfterIt() {
        ContentBlock headingA = makeHeading("A");
        ContentBlock bodyA = makeText(24);
        ContentBlock headingB = makeHeading("B");
        ContentBlock bodyB = makeText(24);

        assertThat(HeadingChunker.split(List.of(headingA, bodyA, headingB, bodyB), MAX_TOKENS))
            .containsExactly(List.of(headingA, bodyA), List.of(headingB, bodyB));
    }

    @Test
    void shouldPackSeveralSmallSectionsIntoOneChunk() {
        ContentBlock headingA = makeHeading("A");
        ContentBlock bodyA = makeText(9);
        ContentBlock headingB = makeHeading("B");
        ContentBlock bodyB = makeText(9);
        ContentBlock headingC = makeHeading("C");
        ContentBlock bodyC = makeText(15);

        assertThat(HeadingChunker.split(List.of(headingA, bodyA, headingB, bodyB, headingC, bodyC), MAX_TOKENS))
            .containsExactly(List.of(headingA, bodyA, headingB, bodyB), List.of(headingC, bodyC));
    }

    @Test
    void shouldSplitASectionLargerThanAChunkAtBlockBoundaries() {
        ContentBlock heading = makeHeading("A");
        ContentBlock first = makeText(9);
        ContentBlock second = makeText(9);
        ContentBlock third = makeText(9);
        ContentBlock fourth = makeText(9);

        assertThat(HeadingChunker.split(List.of(heading, first, second, third, fourth), MAX_TOKENS))
            .containsExactly(List.of(heading, first, second, third), List.of(fourth));
    }

    @Test
    void shouldGiveABlockLargerThanAChunkAChunkOfItsOwn() {
        ContentBlock small = makeText(3);
        ContentBlock huge = makeText(60);
        ContentBlock after = makeText(3);

        assertThat(HeadingChunker.split(List.of(small, huge, after), MAX_TOKENS))
            .containsExactly(List.of(small), List.of(huge), List.of(after));
    }

    @Test
    void shouldReturnNoChunksForNoBlocks() {
        assertThat(HeadingChunker.split(List.of(), MAX_TOKENS)).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private int nextPosition;

    private ContentBlock makeHeading(String text) {
        return ContentBlock.heading(text, 1, nextPosition++);
    }

    private ContentBlock makeText(int chars) {
        return ContentBlock.text("x".repeat(chars), nextPosition++);
    }
}
