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
import dev.mindforge.domain.model.RunFailure;

class ResolverTest {

    private static final int CAP = 100;
    private static final IndexEntry MITOSIS = makeEntry("concepts/mitosis", "Mitosis", PageType.CONCEPT);
    private static final IndexEntry LESSON = makeEntry("sources/bio-1", "Lesson 1", PageType.SOURCE_SUMMARY);

    @Test
    void shouldCreateAPageFromTheClaimsTitleWhenItsTargetIsHallucinatedOrASourceSummary() {
        Resolver resolver = new Resolver(List.of(LESSON), false);

        resolver.addClaims(List.of(makeClaim("Meiosis", "concepts/no-such-page"), makeClaim("Gametes", "sources/bio-1")));

        assertThat(resolver.finish(List.of(), null, null, CAP).tasks())
            .extracting(PageWriteTask::path, PageWriteTask::title, PageWriteTask::create)
            .containsExactly(tuple("concepts/meiosis", "Meiosis", true), tuple("concepts/gametes", "Gametes", true));
    }

    @Test
    void shouldGroupTwoClaimsReachingOnePathIntoOneRevisionKeepingTheLiveTitle() {
        Resolver resolver = new Resolver(List.of(MITOSIS), false);

        resolver.addClaims(List.of(makeClaim("Cell division", "concepts/mitosis"), makeClaim("Mitosis", null)));

        assertThat(resolver.finish(List.of(), null, null, CAP).tasks()).singleElement().satisfies(task -> {
            assertThat(task).extracting(PageWriteTask::path, PageWriteTask::title, PageWriteTask::create)
                .containsExactly("concepts/mitosis", "Mitosis", false);
            assertThat(task.claims()).extracting(Claim::targetPath).containsOnly("concepts/mitosis").hasSize(2);
        });
    }

    @Test
    void shouldLetALaterChunkTargetAPageAnEarlierChunkPlanned() {
        Resolver resolver = new Resolver(List.of(), false);
        resolver.addClaims(List.of(makeClaim("Mitosis", null)));
        assertThat(resolver.planned()).containsExactly(new PlannedPage("concepts/mitosis", "Mitosis"));

        resolver.addClaims(List.of(makeClaim("Mitotic division", "concepts/mitosis")));

        assertThat(resolver.finish(List.of(), null, null, CAP).tasks()).singleElement()
            .satisfies(task -> assertThat(task.claims()).hasSize(2));
    }

    @Test
    void shouldNotLetAClaimTargetAPathPlannedInTheSameChunk() {
        Resolver resolver = new Resolver(List.of(), false);

        resolver.addClaims(List.of(makeClaim("Mitosis", null), makeClaim("Division", "concepts/mitosis")));

        assertThat(resolver.finish(List.of(), null, null, CAP).tasks()).extracting(PageWriteTask::path)
            .containsExactly("concepts/mitosis", "concepts/division");
    }

    @Test
    void shouldFailLoudlyAndNotRetryableOverThePageTaskCapBeforeAnyWrite() {
        Resolver resolver = new Resolver(List.of(), false);
        resolver.addClaims(List.of(makeClaim("A", null), makeClaim("B", null), makeClaim("C", null)));

        assertThatExceptionOfType(IngestRunFailedException.class)
            .isThrownBy(() -> resolver.finish(List.of(), "sources/bio-1", "Lesson", 2))
            .withMessage("would write 3 pages (limit 2) — split the document")
            .satisfies(failure -> assertThat(failure.retryable()).isFalse());
    }

    @Test
    void shouldAddTheSourceSummaryTaskWithTheLessonTitle() {
        Resolver resolver = new Resolver(List.of(LESSON), false);

        assertThat(resolver.finish(List.of(), "sources/bio-1", "Lesson 1 — new version", CAP).tasks())
            .extracting(PageWriteTask::path, PageWriteTask::type, PageWriteTask::title, PageWriteTask::create)
            .containsExactly(tuple("sources/bio-1", PageType.SOURCE_SUMMARY, "Lesson 1 — new version", false));
    }

    @Test
    void shouldFailACreateTaskWhoseTitleIsInvalid() {
        Resolver resolver = new Resolver(List.of(), false);
        resolver.addClaims(List.of(makeClaim("", null), makeClaim("x".repeat(201), null)));

        Resolver.Plan plan = resolver.finish(List.of(), null, null, CAP);

        assertThat(plan.tasks()).isEmpty();
        assertThat(plan.failures()).extracting(RunFailure::reason).containsOnly("invalid title").hasSize(2);
    }

    @Test
    void shouldDeleteALiveConceptAndDropADeleteOfASourceSummaryOrAMissingPage() {
        Resolver resolver = new Resolver(List.of(MITOSIS, LESSON), false);

        Resolver.Plan plan = resolver.finish(List.of(new EditItem.Delete("concepts/mitosis"),
            new EditItem.Delete("sources/bio-1"), new EditItem.Delete("concepts/missing")), null, null, CAP);

        assertThat(plan.deletions()).containsExactly("concepts/mitosis");
        assertThat(plan.failures()).extracting(RunFailure::path)
            .containsExactly("sources/bio-1", "concepts/missing");
    }

    @Test
    void shouldDropBothWhenOneEditDeletesAndWritesAPath() {
        Resolver resolver = new Resolver(List.of(MITOSIS), false);
        resolver.addClaims(List.of(makeClaim("Mitosis", "concepts/mitosis")));

        Resolver.Plan plan = resolver.finish(List.of(new EditItem.Delete("concepts/mitosis")), null, null, CAP);

        assertThat(plan.tasks()).isEmpty();
        assertThat(plan.deletions()).isEmpty();
        assertThat(plan.failures()).extracting(RunFailure::item).containsExactly("delete", "claim");
    }

    @Test
    void shouldRetitleALiveConceptWithoutATaskAndRetitleATaskOfTheSamePath() {
        IndexEntry meiosis = makeEntry("concepts/meiosis", "Meiosis", PageType.CONCEPT);
        Resolver resolver = new Resolver(List.of(MITOSIS, meiosis), false);
        resolver.addClaims(List.of(makeClaim("Meiosis", "concepts/meiosis")));

        Resolver.Plan plan = resolver.finish(List.of(new EditItem.Retitle("concepts/mitosis", "Cell mitosis"),
            new EditItem.Retitle("concepts/meiosis", "Reductive meiosis"),
            new EditItem.Retitle("sources/bio-1", "Not allowed")), null, null, CAP);

        assertThat(plan.retitles()).isEqualTo(Map.of("concepts/mitosis", "Cell mitosis"));
        assertThat(plan.tasks()).extracting(PageWriteTask::title).containsExactly("Reductive meiosis");
        assertThat(plan.failures()).extracting(RunFailure::path).containsExactly("sources/bio-1");
    }

    @Test
    void shouldDropTheClaimsOfAnArticleThatReachALivePage() {
        Resolver resolver = new Resolver(List.of(MITOSIS), true);

        resolver.addClaims(List.of(makeClaim("Mitosis", null), makeClaim("Meiosis", null)));

        Resolver.Plan plan = resolver.finish(List.of(), null, null, CAP);
        assertThat(plan.tasks()).extracting(PageWriteTask::path).containsExactly("concepts/meiosis");
        assertThat(plan.failures()).extracting(RunFailure::path).containsExactly("concepts/mitosis");
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static Claim makeClaim(String title, String targetPath) {
        return new Claim("Claim about " + title + ".", title, targetPath, 0, 0);
    }

    private static IndexEntry makeEntry(String path, String title, PageType type) {
        return new IndexEntry(path, title, "Description.", type);
    }
}
