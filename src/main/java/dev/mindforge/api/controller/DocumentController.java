package dev.mindforge.api.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.response.DocumentAcceptedResponse;
import dev.mindforge.api.dto.response.DocumentResponse;
import dev.mindforge.api.dto.response.RunAcceptedResponse;
import dev.mindforge.api.mapper.DocumentDtoMapper;
import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.model.UploadSource;

@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/documents")
public class DocumentController {

    private final KnowledgeBaseService knowledgeBases;
    private final IngestionService ingestion;
    private final DocumentDtoMapper mapper;

    public DocumentController(KnowledgeBaseService knowledgeBases, IngestionService ingestion,
                              DocumentDtoMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.ingestion = ingestion;
        this.mapper = mapper;
    }

    @Operation(summary = "Upload a document; its ingest run starts after the response")
    @ApiResponse(responseCode = "202", description = "Accepted, or the id of the identical document already here")
    @ApiResponse(responseCode = "409", description = "The lesson exists; send newVersion or another lessonId")
    @ApiResponse(responseCode = "422", description = "Refused upload, invalid lesson id, or a new version of no lesson")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentAcceptedResponse upload(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                           @RequestPart("file") MultipartFile file,
                                           @RequestParam(required = false) String lessonId,
                                           @RequestParam(defaultValue = "false") boolean newVersion)
        throws IOException {
        knowledgeBases.requireOwner(kbId, user.userId());
        return new DocumentAcceptedResponse(ingestion.ingest(new DocumentUpload(kbId, user.userId(), UploadSource.API,
            file.getOriginalFilename(), file.getContentType(), file.getBytes(), lessonId, newVersion)));
    }

    @Operation(summary = "List uploaded documents with their latest run")
    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return ingestion.documents(kbId).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Get a document with its latest run")
    @GetMapping("/{documentId}")
    public DocumentResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                @PathVariable UUID documentId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(ingestion.document(kbId, documentId));
    }

    @Operation(summary = "Retry a document whose latest run failed")
    @ApiResponse(responseCode = "202", description = "Queued")
    @ApiResponse(responseCode = "409", description = "The latest run is not FAILED")
    @PostMapping("/{documentId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunAcceptedResponse retry(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                     @PathVariable UUID documentId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return new RunAcceptedResponse(ingestion.retry(kbId, documentId));
    }
}
