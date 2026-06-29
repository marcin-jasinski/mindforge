package dev.mindforge.infrastructure.persistence.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.ConceptMapData;
import dev.mindforge.domain.model.FlashcardData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "artifacts")
public class ArtifactEntity extends BaseEntity {

    @Id
    @Column(name = "artifact_id", updatable = false, nullable = false)
    private UUID artifactId;

    @Column(name = "document_id", nullable = false, unique = true)
    private UUID documentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private UUID knowledgeBaseId;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_key_points", columnDefinition = "jsonb", nullable = false)
    private List<String> summaryKeyPoints = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "flashcards", columnDefinition = "jsonb", nullable = false)
    private List<FlashcardData> flashcards = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "concept_map", columnDefinition = "jsonb")
    private ConceptMapData conceptMap;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiz_questions", columnDefinition = "jsonb", nullable = false)
    private List<String> quizQuestions = new ArrayList<>();

    @Column(name = "relevance_passed")
    private Boolean relevancePassed;

    @Column(name = "relevance_reason", columnDefinition = "TEXT")
    private String relevanceReason;

    @Column(name = "relevance_confidence")
    private Float relevanceConfidence;

    @Column(name = "completed_step", length = 100)
    private String completedStep;

    public UUID getArtifactId() { return artifactId; }
    public void setArtifactId(UUID artifactId) { this.artifactId = artifactId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public List<String> getSummaryKeyPoints() { return summaryKeyPoints; }
    public void setSummaryKeyPoints(List<String> summaryKeyPoints) { this.summaryKeyPoints = summaryKeyPoints; }

    public List<FlashcardData> getFlashcards() { return flashcards; }
    public void setFlashcards(List<FlashcardData> flashcards) { this.flashcards = flashcards; }

    public ConceptMapData getConceptMap() { return conceptMap; }
    public void setConceptMap(ConceptMapData conceptMap) { this.conceptMap = conceptMap; }

    public List<String> getQuizQuestions() { return quizQuestions; }
    public void setQuizQuestions(List<String> quizQuestions) { this.quizQuestions = quizQuestions; }

    public Boolean getRelevancePassed() { return relevancePassed; }
    public void setRelevancePassed(Boolean relevancePassed) { this.relevancePassed = relevancePassed; }

    public String getRelevanceReason() { return relevanceReason; }
    public void setRelevanceReason(String relevanceReason) { this.relevanceReason = relevanceReason; }

    public Float getRelevanceConfidence() { return relevanceConfidence; }
    public void setRelevanceConfidence(Float relevanceConfidence) { this.relevanceConfidence = relevanceConfidence; }

    public String getCompletedStep() { return completedStep; }
    public void setCompletedStep(String completedStep) { this.completedStep = completedStep; }
}
