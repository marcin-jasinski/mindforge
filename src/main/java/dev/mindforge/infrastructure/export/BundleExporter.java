package dev.mindforge.infrastructure.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.application.wiki.LogRenderer;
import dev.mindforge.application.wiki.PageRenderer;
import dev.mindforge.domain.model.Identifier;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.SourceCitation;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.BundleQuery;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.domain.port.WikiStore;

/**
 * Renders a knowledge base as an OKF bundle (T11): {@code index.md}, {@code log.md} and one file per live page —
 * SnakeYAML frontmatter, the stored body with supersession notes and projected citations. Everything is read in one
 * read-only snapshot, without the lease, validated before a byte is sent and streamed a chunk of pages at a time. It reads no study table and no run's
 * cost, step versions, failures or findings.
 */
public class BundleExporter {

    private static final Logger log = LoggerFactory.getLogger(BundleExporter.class);

    public static final String INDEX = "index.md";
    public static final String LOG = "log.md";

    /** Page bodies are read this many at a time, so heap holds a chunk of the wiki and never all of it. */
    private static final int CHUNK = 50;

    private final WikiStore wiki;
    private final BundleQuery bundleQuery;
    private final RunReportQuery reports;

    public BundleExporter(WikiStore wiki, BundleQuery bundleQuery, RunReportQuery reports) {
        this.wiki = wiki;
        this.bundleQuery = bundleQuery;
        this.reports = reports;
    }

    /** The zip's root directory, which the download's filename repeats. */
    public static String root(String knowledgeBaseName) {
        return Identifier.slugify(knowledgeBaseName) + "-okf";
    }

    /**
     * Streams the bundle as a zip under {@link #root}, in one snapshot. The structure — index, log, every path and
     * frontmatter — is validated from the index rows before the first byte; bodies are then read in chunks.
     * The stream is left open.
     *
     * @throws BundleNotConformantException when the rendering breaks OKF — a bug, logged and never shipped
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(UUID kbId, String knowledgeBaseName, OutputStream out) throws IOException {
        List<IndexEntry> entries = wiki.listIndex(kbId).stream()
            .filter(entry -> entry.type().equals(PageType.CONCEPT) || entry.type().equals(PageType.SOURCE_SUMMARY))
            .sorted(Comparator.comparing(IndexEntry::path))
            .toList();
        String index = IndexRenderer.render(entries);
        String logFile = LogRenderer.render(reports.logEntries(kbId));

        // ponytail: validated on frontmatter alone; a body is stored verbatim and no OKF §9 rule reads it
        Map<String, String> structure = new LinkedHashMap<>();
        structure.put(INDEX, index);
        structure.put(LOG, logFile);
        for (IndexEntry entry : entries) {
            structure.put(entry.path() + ".md", frontmatter(entry.type(), entry.title(), entry.description(), Instant.EPOCH));
        }
        List<String> violations = BundleConformanceValidator.violations(structure);
        if (!violations.isEmpty()) {
            log.error("Bundle of knowledge base {} breaks OKF: {}", kbId, violations);
            throw new BundleNotConformantException(violations);
        }

        String root = root(knowledgeBaseName) + "/";
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        write(zip, root + INDEX, index);
        write(zip, root + LOG, logFile);
        List<String> paths = entries.stream().map(IndexEntry::path).toList();
        for (int from = 0; from < paths.size(); from += CHUNK) {
            List<WikiPage> chunk = wiki.findByPaths(kbId, paths.subList(from, Math.min(from + CHUNK, paths.size())))
                .stream().sorted(Comparator.comparing(WikiPage::path)).toList();
            List<UUID> ids = chunk.stream().map(WikiPage::pageId).toList();
            Map<UUID, List<LiveSupersession>> notes = wiki.liveSupersessionsOf(kbId, ids).stream()
                .collect(Collectors.groupingBy(LiveSupersession::supersededPageId));
            Map<UUID, List<SourceCitation>> citations = bundleQuery.citations(kbId, ids).stream()
                .collect(Collectors.groupingBy(SourceCitation::pageId));
            for (WikiPage page : chunk) {
                write(zip, root + page.path() + ".md", frontmatter(page.type(), page.title(), page.description(),
                    page.updatedAt()) + PageRenderer.render(page, notes.getOrDefault(page.pageId(), List.of()),
                    citations.getOrDefault(page.pageId(), List.of())));
            }
        }
        zip.finish();
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String frontmatter(PageType type, String title, String description, Instant updatedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", type.value());
        fields.put("title", title);
        fields.put("description", description);
        fields.put("timestamp", updatedAt.toString());
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setWidth(Integer.MAX_VALUE);
        return "---\n" + new Yaml(options).dump(fields) + "---\n";
    }
}
