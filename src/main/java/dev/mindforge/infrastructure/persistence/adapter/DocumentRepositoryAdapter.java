package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DocumentStatus;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.persistence.jpa.DocumentJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.DocumentEntityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class DocumentRepositoryAdapter implements DocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(DocumentRepositoryAdapter.class);

    private final DocumentJpaRepository jpaRepository;
    private final DocumentEntityMapper mapper;

    public DocumentRepositoryAdapter(DocumentJpaRepository jpaRepository,
                                     DocumentEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Document save(Document document) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(document)));
    }

    @Override
    public Optional<Document> findById(UUID documentId) {
        return jpaRepository.findById(documentId).map(mapper::toDomain);
    }

    @Override
    public Optional<Document> findByContentHash(ContentHash contentHash) {
        return jpaRepository.findByContentHash(contentHash.sha256()).map(mapper::toDomain);
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
            .map(mapper::toDomain)
            .toList();
    }
}
