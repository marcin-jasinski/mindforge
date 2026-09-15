package dev.mindforge.infrastructure.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * read-only snapshot, without the lease, and validated before a byte is sent. It reads no study table and no run's
 * cost, step versions, failures or findings.
 */
public class BundleExporter {

    private static final Logger log = LoggerFactory.getLogger(BundleExporter.class);

    public static final String INDEX = "index.md";
    public static final String LOG = "log.md";

    /** A rendered bundle: its root directory and every file by bundle-relative path. */
    public record Bundle(String root, Map<String, String> files) {}

    private final WikiStore wiki;
    private final BundleQuery bundleQuery;
    private final RunReportQuery reports;

    public BundleExporter(WikiStore wiki, BundleQuery bundleQuery, RunReportQuery reports) {
        this.wiki = wiki;
        this.bundleQuery = bundleQuery;
        this.reports = reports;
    }

    /** @throws BundleNotConformantException when the rendering breaks OKF — a bug, logged and never shipped */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Bundle export(UUID kbId, String knowledgeBaseName) {
        List<WikiPage> pages = new ArrayList<>(wiki.listBodies(kbId, PageType.CONCEPT));
        pages.addAll(wiki.listBodies(kbId, PageType.SOURCE_SUMMARY));
        pages.sort(Comparator.comparing(WikiPage::path));
        List<UUID> ids = pages.stream().map(WikiPage::pageId).toList();
        Map<UUID, List<LiveSupersession>> notes = wiki.liveSupersessionsOf(kbId, ids).stream()
            .collect(Collectors.groupingBy(LiveSupersession::supersededPageId));
        Map<UUID, List<SourceCitation>> citations = bundleQuery.citations(kbId, ids).stream()
            .collect(Collectors.groupingBy(SourceCitation::pageId));

        Map<String, String> files = new LinkedHashMap<>();
        files.put(INDEX, IndexRenderer.render(pages.stream()
            .map(page -> new IndexEntry(page.path(), page.title(), page.description(), page.type()))
            .toList()));
        files.put(LOG, LogRenderer.render(reports.logEntries(kbId)));
        for (WikiPage page : pages) {
            files.put(page.path() + ".md", frontmatter(page) + PageRenderer.render(page,
                notes.getOrDefault(page.pageId(), List.of()), citations.getOrDefault(page.pageId(), List.of())));
        }

        List<String> violations = BundleConformanceValidator.violations(files);
        if (!violations.isEmpty()) {
            log.error("Bundle of knowledge base {} breaks OKF: {}", kbId, violations);
            throw new BundleNotConformantException(violations);
        }
        return new Bundle(Identifier.slugify(knowledgeBaseName) + "-okf", files);
    }

    /** Writes the bundle as a zip whose entries sit under its root directory; the stream is left open. */
    public static void writeZip(Bundle bundle, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> file : bundle.files().entrySet()) {
            zip.putNextEntry(new ZipEntry(bundle.root() + "/" + file.getKey()));
            zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        zip.finish();
    }

    private static String frontmatter(WikiPage page) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", page.type().value());
        fields.put("title", page.title());
        fields.put("description", page.description());
        fields.put("timestamp", page.updatedAt().toString());
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setWidth(Integer.MAX_VALUE);
        return "---\n" + new Yaml(options).dump(fields) + "---\n";
    }
}
