package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.ingest.SupersessionInputs;
import dev.mindforge.domain.model.CandidateSection;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.SupersessionProposal;
import dev.mindforge.domain.model.WikiPage;

class SupersessionInputsTest {

    private static final List<CandidateSection> SHOWN = List.of(
        new CandidateSection("concepts/mitoza", "faza", "Faza", "Profaza trwa godzinę."));

    @Test
    void shouldKeepOnlyProposalsOnShownSectionsBySupersedingConceptsThisRunRevised() {
        List<SupersessionProposal> proposals = List.of(
            new SupersessionProposal("concepts/gamety", "faza", "concepts/mejoza"),
            new SupersessionProposal("sources/bio-1", "streszczenie", "concepts/mejoza"),
            new SupersessionProposal("concepts/mitoza", "faza", "concepts/mitoza"),
            new SupersessionProposal("concepts/mitoza", "faza", "concepts/nie-pisana"),
            new SupersessionProposal("concepts/mitoza", "faza", "concepts/mejoza"),
            new SupersessionProposal("concepts/mitoza", "faza", "concepts/mejoza"));

        assertThat(SupersessionInputs.verify(proposals, SHOWN, Set.of("concepts/mitoza", "concepts/mejoza")))
            .containsExactly(new SupersessionProposal("concepts/mitoza", "faza", "concepts/mejoza"));
    }

    @Test
    void shouldAddWholePagesByLinkCountThenPathUntilTheNextDoesNotFitAndCountTheRest() {
        WikiPage mostLinked = makePage("concepts/z", "# A\n\n" + "x".repeat(30) + "\n\n# B\n\ny\n");
        WikiPage tieFirst = makePage("concepts/a", "# C\n\n" + "x".repeat(30) + "\n");
        WikiPage tieSecond = makePage("concepts/b", "# D\n\n" + "x".repeat(30) + "\n");

        SupersessionInputs.Selection selection = SupersessionInputs.select(List.of(tieSecond, tieFirst, mostLinked),
            Map.of("concepts/z", 3, "concepts/a", 1, "concepts/b", 1), Set.of("concepts/z#b"), 20);

        assertThat(selection.sections()).extracting(CandidateSection::path, CandidateSection::anchor)
            .containsExactly(tuple("concepts/z", "a"), tuple("concepts/a", "c"));
        assertThat(selection.omittedPages()).isEqualTo(1);
    }

    private static WikiPage makePage(String path, String body) {
        return new WikiPage(UUID.randomUUID(), UUID.randomUUID(), path, "Tytuł", "Opis.", PageType.CONCEPT, body, 1,
            Instant.EPOCH, Instant.EPOCH);
    }
}
