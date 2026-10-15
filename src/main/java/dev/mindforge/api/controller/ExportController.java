package dev.mindforge.api.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.infrastructure.export.BundleExporter;

/**
 * The bundle is spooled to a temp file inside the export's snapshot, so the database connection is released before
 * the download starts and a slow client holds only a file handle.
 */
@RestController
public class ExportController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final KnowledgeBaseService knowledgeBases;
    private final BundleExporter exporter;

    public ExportController(KnowledgeBaseService knowledgeBases, BundleExporter exporter) {
        this.knowledgeBases = knowledgeBases;
        this.exporter = exporter;
    }

    @Operation(summary = "Download the knowledge base as an OKF bundle")
    @ApiResponse(responseCode = "200", description = "A zip rendered and validated before it is sent")
    @GetMapping(value = "/api/v1/knowledge-bases/{kbId}/export", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> export(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable UUID kbId) throws IOException {
        String name = knowledgeBases.get(kbId, user.userId()).name();
        Path spool = Files.createTempFile("mindforge-export-", ".zip");
        try (OutputStream out = Files.newOutputStream(spool)) {
            exporter.export(kbId, name, out);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(spool);
            throw e;
        }
        return ResponseEntity.ok()
            .contentType(ZIP)
            .contentLength(Files.size(spool))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(BundleExporter.root(name) + ".zip").build().toString())
            .body(out -> {
                try {
                    Files.copy(spool, out);
                } finally {
                    Files.deleteIfExists(spool);
                }
            });
    }
}
