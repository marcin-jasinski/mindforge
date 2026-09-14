package dev.mindforge.infrastructure.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBaseEntity extends BaseEntity {

    @Id
    @Column(name = "kb_id", updatable = false, nullable = false)
    private UUID kbId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Formula("(SELECT COUNT(*) FROM wiki_pages p WHERE p.knowledge_base_id = kb_id)")
    private long pageCount;

    public UUID getKbId() { return kbId; }
    public void setKbId(UUID kbId) { this.kbId = kbId; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getPageCount() { return pageCount; }
}
