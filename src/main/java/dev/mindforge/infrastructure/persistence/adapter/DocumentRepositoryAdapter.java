package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.persistence.jpa.DocumentJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.DocumentEntityMapper;

public class DocumentRepositoryAdapter implements DocumentRepository {

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
    public Optional<Document> findById(UUID knowledgeBaseId, UUID documentId) {
        return jpaRepository.findByKnowledgeBaseIdAndDocumentId(knowledgeBaseId, documentId)
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Document> findByContentHash(UUID knowledgeBaseId, ContentHash contentHash) {
        return jpaRepository.findByKnowledgeBaseIdAndContentHash(knowledgeBaseId, contentHash.sha256())
            .map(mapper::toDomain);
    }

    @Override
    public List<Document> listByKnowledgeBase(UUID knowledgeBaseId) {
        return jpaRepository.findByKnowledgeBaseId(knowledgeBaseId)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }
}
