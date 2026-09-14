package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.ingest.Resolver;
import dev.mindforge.domain.model.Claim;
import dev.mindforge.domain.model.EditItem;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.IngestRunFailedException;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWriteTask;
import dev.mindforge.domain.model.PlannedPage;

class ResolverTest {

    private static final int CAP = 100;
    private static final IndexEntry MITOZA = makeEntry("concepts/mitoza", "Mitoza", PageType.CONCEPT);
    private static final IndexEntry LESSON = makeEntry("sources/bio-1", "Lekcja 1", PageType.SOURCE_SUMMARY);

    @Test
    void shouldCreateAPageFromTheClaimsTitleWhenItsTargetIsHallucinatedOrASourceSummary() {
        Resolver resolver = new Resolver(List.of(LESSON), false);

        resolver.addClaims(List.of(makeClaim("Mejoza", "concepts/nie-ma-takiej"), makeClaim("Gamety", "sources/bio-1")));

        assertThat(resolver.finish(List.of(), null, null, CAP).tasks())
            .extracting(PageWriteTask::path, PageWriteTask::title, PageWriteTask::create)
            .containsExactly(tuple("concepts/mejoza", "Mejoza", true), tuple("concepts/gamety", "Gamety", true));
    }

    @Test
    void shouldGroupTwoClaimsReachingOnePathIntoOneRevisionKeepingTheLiveTitle() {
        Resolver resolver = new Resolver(List.of(MITOZA), false);

        resolver.addClaims(List.of(makeClaim("Podział komórki", "concepts/mitoza"), makeClaim("Mitoza", null)));

        assertThat(resolver.finish(List.of(), null, null, CAP).tasks()).singleElement().satisfies(task -> {
            assertThat(task).extracting(PageWriteTask::path, PageWriteTask::title, PageWriteTask::create)
                .containsExactly("concepts/mitoza", "Mitoza", false);
            assertThat(task.claims()).extracting(Claim::targetPath).containsOnly("concepts/mitoza").hasSize(2);
        });
    }

    @Test
    void shouldLetALaterChunkTargetAPageAnEarlierChunkPlanned() {
        Resolver resolver = new Resolver(List.of(), false);
        resolver.addClaims(List.of(makeClaim("Mitoza", null)));
        assertThat(resolver.planned()).containsExactly(new PlannedPage("concepts/mitoza", "Mitoza"));

        resolver.addClaims(List.of(makeClaim("Podział mitotyczny", "concepts/mitoza")));

        assertThat(resolver.finish(List.of(), null, null, CAP).tasks()).singleElement()
            .satisfies(task -> assertThat(task.claims()).hasSize(2));
    }

    @Test
    void shouldNotLetAClaimTargetAPathPlannedInTheSameChunk() {
        Resolver resolver = new Resolver(List.of(), false);

        resolver.addClaims(List.of(makeClaim("Mitoza", null), makeClaim("Podział", "concepts/mitoza")));

        assertThat(resolver.finish(List.of(), null, null, CAP).tasks()).extracting(PageWriteTask::path)
            .containsExactly("concepts/mitoza", "concepts/podzial");
    }

    @Test
    void shouldFailLoudlyAndNotRetryableOverThePageTaskCapBeforeAnyWrite() {
        Resolver resolver = new Resolver(List.of(), false);
        resolver.addClaims(List.of(makeClaim("A", null), makeClaim("B", null), makeClaim("C", null)));

        assertThatExceptionOfType(IngestRunFailedException.class)
            .isThrownBy(() -> resolver.finish(List.of(), "sources/bio-1", "Lekcja", 2))
            .withMessage("would write 3 pages (limit 2) — split the document")
            .satisfies(failure -> assertThat(failure.retryable()).isFalse());
    }

    @Test
    void shouldAddTheSourceSummaryTaskWithTheLessonTitle() {
        Resolver resolver = new Resolver(List.of(LESSON), false);

        assertThat(resolver.finish(List.of(), "sources/bio-1", "Lekcja 1 — nowa wersja", CAP).tasks())
            .extracting(PageWriteTask::path, PageWriteTask::type, PageWriteTask::title, PageWriteTask::create)
            .containsExactly(tuple("sources/bio-1", PageType.SOURCE_SUMMARY, "Lekcja 1 — nowa wersja", false));
    }

    @Test
    void shouldFailACreateTaskWhoseTitleIsInvalid() {
        Resolver resolver = new Resolver(List.of(), false);
        resolver.addClaims(List.of(makeClaim("", null), makeClaim("x".repeat(201), null)));

        Resolver.Plan plan = resolver.finish(List.of(), null, null, CAP);

        assertThat(plan.tasks()).isEmpty();
        assertThat(plan.failures()).extracting(failure -> failure.get("reason")).containsOnly("invalid title").hasSize(2);
    }

    @Test
    void shouldDeleteALiveConceptAndDropADeleteOfASourceSummaryOrAMissingPage() {
        Resolver resolver = new Resolver(List.of(MITOZA, LESSON), false);

        Resolver.Plan plan = resolver.finish(List.of(new EditItem.Delete("concepts/mitoza"),
            new EditItem.Delete("sources/bio-1"), new EditItem.Delete("concepts/nie-ma")), null, null, CAP);

        assertThat(plan.deletions()).containsExactly("concepts/mitoza");
        assertThat(plan.failures()).extracting(failure -> failure.get("path"))
            .containsExactly("sources/bio-1", "concepts/nie-ma");
    }

    @Test
    void shouldDropBothWhenOneEditDeletesAndWritesAPath() {
        Resolver resolver = new Resolver(List.of(MITOZA), false);
        resolver.addClaims(List.of(makeClaim("Mitoza", "concepts/mitoza")));

        Resolver.Plan plan = resolver.finish(List.of(new EditItem.Delete("concepts/mitoza")), null, null, CAP);

        assertThat(plan.tasks()).isEmpty();
        assertThat(plan.deletions()).isEmpty();
        assertThat(plan.failures()).extracting(failure -> failure.get("item")).containsExactly("delete", "claim");
    }

    @Test
    void shouldRetitleALiveConceptWithoutATaskAndRetitleATaskOfTheSamePath() {
        IndexEntry mejoza = makeEntry("concepts/mejoza", "Mejoza", PageType.CONCEPT);
        Resolver resolver = new Resolver(List.of(MITOZA, mejoza), false);
        resolver.addClaims(List.of(makeClaim("Mejoza", "concepts/mejoza")));

        Resolver.Plan plan = resolver.finish(List.of(new EditItem.Retitle("concepts/mitoza", "Mitoza komórkowa"),
            new EditItem.Retitle("concepts/mejoza", "Mejoza redukcyjna"),
            new EditItem.Retitle("sources/bio-1", "Nie wolno")), null, null, CAP);

        assertThat(plan.retitles()).isEqualTo(Map.of("concepts/mitoza", "Mitoza komórkowa"));
        assertThat(plan.tasks()).extracting(PageWriteTask::title).containsExactly("Mejoza redukcyjna");
        assertThat(plan.failures()).extracting(failure -> failure.get("path")).containsExactly("sources/bio-1");
    }

    @Test
    void shouldDropTheClaimsOfAnArticleThatReachALivePage() {
        Resolver resolver = new Resolver(List.of(MITOZA), true);

        resolver.addClaims(List.of(makeClaim("Mitoza", null), makeClaim("Mejoza", null)));

        Resolver.Plan plan = resolver.finish(List.of(), null, null, CAP);
        assertThat(plan.tasks()).extracting(PageWriteTask::path).containsExactly("concepts/mejoza");
        assertThat(plan.failures()).extracting(failure -> failure.get("path")).containsExactly("concepts/mitoza");
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static Claim makeClaim(String title, String targetPath) {
        return new Claim("Twierdzenie o " + title + ".", title, targetPath, 0, 0);
    }

    private static IndexEntry makeEntry(String path, String title, PageType type) {
        return new IndexEntry(path, title, "Opis.", type);
    }
}
