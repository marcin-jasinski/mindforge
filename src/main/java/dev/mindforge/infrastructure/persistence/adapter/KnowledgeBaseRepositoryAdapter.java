package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import dev.mindforge.infrastructure.persistence.entity.KnowledgeBaseEntity;
import dev.mindforge.infrastructure.persistence.jpa.KnowledgeBaseJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.KnowledgeBaseEntityMapper;

@Transactional
public class KnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepository {

    private final KnowledgeBaseJpaRepository knowledgeBases;
    private final KnowledgeBaseEntityMapper mapper;

    public KnowledgeBaseRepositoryAdapter(KnowledgeBaseJpaRepository knowledgeBases, KnowledgeBaseEntityMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.mapper = mapper;
    }

    @Override
    public KnowledgeBase insert(KnowledgeBase knowledgeBase) {
        return mapper.toDomain(knowledgeBases.saveAndFlush(mapper.toEntity(knowledgeBase)));
    }

    @Override
    public Optional<KnowledgeBase> findById(UUID kbId) {
        return knowledgeBases.findById(kbId).map(mapper::toDomain);
    }

    @Override
    public List<KnowledgeBase> listByOwner(UUID ownerId) {
        return knowledgeBases.findByOwnerIdOrderByCreatedAt(ownerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public KnowledgeBase update(UUID kbId, String name, String description) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBases.findById(kbId)
            .orElseThrow(() -> new NotFoundException("Knowledge base"));
        knowledgeBase.setName(name);
        knowledgeBase.setDescription(description);
        return mapper.toDomain(knowledgeBases.saveAndFlush(knowledgeBase));
    }

    @Override
    public boolean deleteIfIdle(UUID kbId) {
        return knowledgeBases.deleteIfIdle(kbId) == 1;
    }
}
