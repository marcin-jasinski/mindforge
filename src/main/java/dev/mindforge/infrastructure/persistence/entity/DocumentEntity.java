package dev.mindforge.infrastructure.persistence.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.ContentBlock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "documents")
public class DocumentEntity extends BaseEntity {

    @Id
    @Column(name = "document_id", updatable = false, nullable = false)
    private UUID documentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private UUID knowledgeBaseId;

    @Column(name = "lesson_id", nullable = false, length = 80)
    private String lessonId;

    @Column(name = "lesson_title", nullable = false)
    private String lessonTitle;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_blocks", columnDefinition = "jsonb", nullable = false)
    private List<ContentBlock> contentBlocks = new ArrayList<>();

    @Column(name = "upload_source", nullable = false, length = 50)
    private String uploadSource;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public String getLessonId() { return lessonId; }
    public void setLessonId(String lessonId) { this.lessonId = lessonId; }

    public String getLessonTitle() { return lessonTitle; }
    public void setLessonTitle(String lessonTitle) { this.lessonTitle = lessonTitle; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getOriginalContent() { return originalContent; }
    public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }

    public List<ContentBlock> getContentBlocks() { return contentBlocks; }
    public void setContentBlocks(List<ContentBlock> contentBlocks) { this.contentBlocks = contentBlocks; }

    public String getUploadSource() { return uploadSource; }
    public void setUploadSource(String uploadSource) { this.uploadSource = uploadSource; }

    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
