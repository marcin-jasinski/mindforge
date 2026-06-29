package dev.mindforge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DocumentStatus;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.persistence.entity.DocumentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Adapts the JPA {@link DocumentJpaRepository} to the domain {@link DocumentRepository} port. */
public class DocumentRepositoryAdapter implements DocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(DocumentRepositoryAdapter.class);

    private final DocumentJpaRepository jpaRepository;

    public DocumentRepositoryAdapter(DocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Document save(Document document) {
        DocumentEntity entity = toEntity(document);
        DocumentEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Document> findById(UUID documentId) {
        return jpaRepository.findById(documentId).map(this::toDomain);
    }

    @Override
    public Optional<Document> findByContentHash(ContentHash contentHash) {
        return jpaRepository.findByContentHash(contentHash.sha256()).map(this::toDomain);
    }

    @Override
    @Transactional
    public void updateStatus(UUID documentId, DocumentStatus status) {
        log.debug("Updating document {} status to {}", documentId, status);
        jpaRepository.updateStatus(documentId, status.name());
    }

    @Override
    public List<Document> listByKnowledgeBase(UUID knowledgeBaseId) {
        return jpaRepository.findByKnowledgeBaseId(knowledgeBaseId)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    // ---------------------------------------------------------------------------
    // Mapping helpers
    // ---------------------------------------------------------------------------

    private DocumentEntity toEntity(Document d) {
        DocumentEntity e = new DocumentEntity();
        e.setDocumentId(d.documentId());
        e.setKnowledgeBaseId(d.knowledgeBaseId());
        e.setLessonId(d.lessonIdentity().lessonId());
        e.setLessonTitle(d.lessonIdentity().title());
        e.setContentHash(d.contentHash().sha256());
        e.setSourceFilename(d.sourceFilename());
        e.setMimeType(d.mimeType());
        e.setOriginalContent(d.originalContent());
        e.setContentBlocks(d.contentBlocks());
        e.setUploadSource(d.uploadSource().name());
        e.setUploadedBy(d.uploadedBy());
        e.setStatus(d.status().name());
        return e;
    }

    private Document toDomain(DocumentEntity e) {
        return new Document(
            e.getDocumentId(),
            e.getKnowledgeBaseId(),
            new LessonIdentity(e.getLessonId(), e.getLessonTitle()),
            new ContentHash(e.getContentHash()),
            e.getSourceFilename(),
            e.getMimeType(),
            e.getOriginalContent(),
            e.getContentBlocks(),
            UploadSource.valueOf(e.getUploadSource()),
            e.getUploadedBy(),
            DocumentStatus.valueOf(e.getStatus()),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
