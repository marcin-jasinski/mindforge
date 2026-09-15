package dev.mindforge.api.controller;

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
    @GetMapping(value = "/api/knowledge-bases/{kbId}/export", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> export(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable UUID kbId) {
        BundleExporter.Bundle bundle = exporter.export(kbId, knowledgeBases.get(kbId, user.userId()).name());
        return ResponseEntity.ok()
            .contentType(ZIP)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(bundle.root() + ".zip").build().toString())
            .body(out -> BundleExporter.writeZip(bundle, out));
    }
}
