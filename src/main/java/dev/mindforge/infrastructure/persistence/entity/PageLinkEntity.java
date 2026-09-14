package dev.mindforge.infrastructure.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A link derived from a page body; deleted with its page by the database. */
@Entity
@Table(name = "page_links")
public class PageLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id", updatable = false, nullable = false)
    private Long linkId;

    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Column(name = "source_page_id", nullable = false, updatable = false)
    private UUID sourcePageId;

    @Column(name = "target_path", nullable = false, updatable = false, length = 89)
    private String targetPath;

    @Column(name = "fragment", updatable = false, length = 80)
    private String fragment;

    public Long getLinkId() { return linkId; }
    public void setLinkId(Long linkId) { this.linkId = linkId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public UUID getSourcePageId() { return sourcePageId; }
    public void setSourcePageId(UUID sourcePageId) { this.sourcePageId = sourcePageId; }

    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }

    public String getFragment() { return fragment; }
    public void setFragment(String fragment) { this.fragment = fragment; }
}
