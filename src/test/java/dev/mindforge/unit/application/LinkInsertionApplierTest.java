package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.wiki.LinkInsertionApplier;
import dev.mindforge.domain.model.LinkInsertion;

class LinkInsertionApplierTest {

    private static final String PAGE = "concepts/mitosis";
    private static final Map<String, String> TARGETS = Map.of("concepts/meiosis", "# Stages\n\nProphase.\n");

    @Test
    void shouldWrapTheFirstEligibleOccurrenceOfThePhrase() {
        LinkInsertionApplier.Result result = apply("In `meiosis` and in meiosis.\n", insertion("meiosis", "stages"));

        assertThat(result.body()).isEqualTo("In `meiosis` and in [meiosis](/concepts/meiosis.md#stages).\n");
        assertThat(result.applied()).isEqualTo(1);
    }

    @Test
    void shouldRejectAPhraseThatIsNotInTheBody() {
        assertThat(apply("Cell division.\n", insertion("meiosis", null)))
            .extracting(LinkInsertionApplier.Result::applied, LinkInsertionApplier.Result::dropped)
            .containsExactly(0, 1);
    }

    @Test
    void shouldRejectATargetThatIsNotLiveOrSuccessfullyDrafted() {
        LinkInsertion failedCreate = new LinkInsertion(PAGE, "gametes", "concepts/gametes", null);

        assertThat(apply("Gametes form.\n", failedCreate).body()).isEqualTo("Gametes form.\n");
    }

    @Test
    void shouldRejectAnOccurrenceInsideAFencedBlockOrAHeading() {
        String body = "# meiosis\n\n```\nmeiosis\n```\n";

        assertThat(apply(body, insertion("meiosis", null))).extracting(LinkInsertionApplier.Result::body,
            LinkInsertionApplier.Result::dropped).containsExactly(body, 1);
    }

    @Test
    void shouldRejectAFragmentThatIsNotALevelOneAnchorOfTheTarget() {
        assertThat(apply("And meiosis.\n", insertion("meiosis", "prophase")).dropped()).isEqualTo(1);
    }

    @Test
    void shouldRejectALinkToThePageItself() {
        assertThat(apply("Mitosis is division.\n", new LinkInsertion(PAGE, "Mitosis", PAGE, null)).dropped())
            .isEqualTo(1);
    }

    @Test
    void shouldRejectAPhraseThatWouldChangeTheProseOnceLinksAreStripped() {
        assertThat(apply("A [b] c.\n", insertion("[b]", null)).dropped()).isEqualTo(1);
    }

    private static LinkInsertionApplier.Result apply(String body, LinkInsertion insertion) {
        return LinkInsertionApplier.apply(PAGE, body, List.of(insertion), TARGETS);
    }

    private static LinkInsertion insertion(String phrase, String fragment) {
        return new LinkInsertion(PAGE, phrase, "concepts/meiosis", fragment);
    }
}
