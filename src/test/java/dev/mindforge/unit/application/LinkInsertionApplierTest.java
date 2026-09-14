package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.wiki.LinkInsertionApplier;
import dev.mindforge.domain.model.LinkInsertion;

class LinkInsertionApplierTest {

    private static final String PAGE = "concepts/mitoza";
    private static final Map<String, String> TARGETS = Map.of("concepts/mejoza", "# Etapy\n\nProfaza.\n");

    @Test
    void shouldWrapTheFirstEligibleOccurrenceOfThePhrase() {
        LinkInsertionApplier.Result result = apply("W `mejoza` i w mejoza.\n", insertion("mejoza", "etapy"));

        assertThat(result.body()).isEqualTo("W `mejoza` i w [mejoza](/concepts/mejoza.md#etapy).\n");
        assertThat(result.applied()).isEqualTo(1);
    }

    @Test
    void shouldRejectAPhraseThatIsNotInTheBody() {
        assertThat(apply("Podział komórki.\n", insertion("mejoza", null)))
            .extracting(LinkInsertionApplier.Result::applied, LinkInsertionApplier.Result::dropped)
            .containsExactly(0, 1);
    }

    @Test
    void shouldRejectATargetThatIsNotLiveOrSuccessfullyDrafted() {
        LinkInsertion failedCreate = new LinkInsertion(PAGE, "gamety", "concepts/gamety", null);

        assertThat(apply("Powstają gamety.\n", failedCreate).body()).isEqualTo("Powstają gamety.\n");
    }

    @Test
    void shouldRejectAnOccurrenceInsideAFencedBlockOrAHeading() {
        String body = "# mejoza\n\n```\nmejoza\n```\n";

        assertThat(apply(body, insertion("mejoza", null))).extracting(LinkInsertionApplier.Result::body,
            LinkInsertionApplier.Result::dropped).containsExactly(body, 1);
    }

    @Test
    void shouldRejectAFragmentThatIsNotALevelOneAnchorOfTheTarget() {
        assertThat(apply("I mejoza.\n", insertion("mejoza", "profaza")).dropped()).isEqualTo(1);
    }

    @Test
    void shouldRejectALinkToThePageItself() {
        assertThat(apply("Mitoza to podział.\n", new LinkInsertion(PAGE, "Mitoza", PAGE, null)).dropped())
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
        return new LinkInsertion(PAGE, phrase, "concepts/mejoza", fragment);
    }
}
