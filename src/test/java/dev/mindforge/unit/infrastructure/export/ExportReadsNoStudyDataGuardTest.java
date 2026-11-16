package dev.mindforge.unit.infrastructure.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.jpa.repository.Query;

import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.infrastructure.persistence.jpa.IngestRunJpaRepository;

/**
 * Tripwire for "study data never enters an export": the export code and the two query adapters it reads through
 * reference no study or interaction type or table, and the run log query selects no run internals.
 */
class ExportReadsNoStudyDataGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String EXPORT_DIR = "dev/mindforge/infrastructure/export/";
    private static final List<String> EXPORT_QUERY_ADAPTERS = List.of(
        "dev/mindforge/infrastructure/persistence/adapter/BundleQueryAdapter.java",
        "dev/mindforge/infrastructure/persistence/adapter/RunReportQueryAdapter.java");

    private static final Pattern STUDY_MARKER = Pattern.compile("\\b(FlashcardJpaRepository|QuizSessionJpaRepository"
        + "|StudyEventJpaRepository|InteractionJpaRepository|InteractionTurnJpaRepository|FlashcardEntity"
        + "|QuizSessionEntity|StudyEventEntity|InteractionEntity|InteractionTurnEntity|QuizSessionStore"
        + "|StudyProgressStore|InteractionStore|flashcards|quiz_sessions|study_events|interactions"
        + "|interaction_turns)\\b");

    private static final Pattern RUN_INTERNAL = Pattern.compile(
        "\\b(cost|step_versions|stepVersions|failures|findings)\\b", Pattern.CASE_INSENSITIVE);

    @Test
    void shouldFindNoStudyOrInteractionTypeInTheExportCode() {
        assertThat(offenders(MAIN_SOURCES))
            .as("export code references a study or interaction type or table (study data never enters an export)")
            .isEmpty();
    }

    @Test
    void shouldSelectNoRunInternalsInTheRunLogQuery() throws NoSuchMethodException {
        String query = IngestRunJpaRepository.class
            .getMethod("findLogEntries", UUID.class, RunStatus.class)
            .getAnnotation(Query.class)
            .value();

        assertThat(query).isNotBlank();
        assertThat(RUN_INTERNAL.matcher(query).find())
            .as("findLogEntries selects a run internal: %s", query)
            .isFalse();
    }

    @Test
    void shouldNotAcceptAQueryThatSelectsARunInternal() {
        assertThat(RUN_INTERNAL.matcher("SELECT r.runId, r.cost FROM IngestRunEntity r").find()).isTrue();
        assertThat(RUN_INTERNAL.matcher("SELECT r.stepVersions FROM IngestRunEntity r").find()).isTrue();
        assertThat(RUN_INTERNAL.matcher("SELECT r.supersessionCount FROM IngestRunEntity r").find()).isFalse();
    }

    @Test
    void shouldReportAnExportFileOrQueryAdapterThatReferencesStudyData(@TempDir Path root) throws IOException {
        write(root, EXPORT_DIR + "BundleExporter.java", "class BundleExporter { FlashcardJpaRepository cards; }");
        write(root, EXPORT_DIR + "sub/Notes.java", "class Notes { String sql = \"SELECT * FROM quiz_sessions\"; }");
        write(root, EXPORT_DIR + "Clean.java", "class Clean { WikiStore wiki; }");
        write(root, EXPORT_QUERY_ADAPTERS.get(1), "class RunReportQueryAdapter { InteractionStore store; }");
        write(root, "dev/mindforge/infrastructure/persistence/adapter/QuizSessionStoreAdapter.java",
            "class QuizSessionStoreAdapter { QuizSessionJpaRepository sessions; }");

        assertThat(offenders(root)).containsExactly(
            EXPORT_DIR + "BundleExporter.java",
            EXPORT_DIR + "sub/Notes.java",
            EXPORT_QUERY_ADAPTERS.get(1));
    }

    private static List<String> offenders(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .filter(relative -> relative.startsWith(EXPORT_DIR) || EXPORT_QUERY_ADAPTERS.contains(relative))
                .filter(relative -> STUDY_MARKER.matcher(read(root.resolve(relative))).find())
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
