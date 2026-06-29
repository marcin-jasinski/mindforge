package dev.mindforge.infrastructure.persistence.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "step_checkpoints")
public class StepCheckpointEntity {

    @EmbeddedId
    private StepCheckpointId id;

    @Column(name = "fingerprint", nullable = false, length = 16)
    private String fingerprint;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public StepCheckpointId getId() { return id; }
    public void setId(StepCheckpointId id) { this.id = id; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    // ---------------------------------------------------------------------------
    // Composite key
    // ---------------------------------------------------------------------------

    @Embeddable
    public static class StepCheckpointId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "document_id", nullable = false)
        private UUID documentId;

        @Column(name = "output_key", nullable = false, length = 100)
        private String outputKey;

        protected StepCheckpointId() {}

        public StepCheckpointId(UUID documentId, String outputKey) {
            this.documentId = documentId;
            this.outputKey = outputKey;
        }

        public UUID getDocumentId() { return documentId; }
        public void setDocumentId(UUID documentId) { this.documentId = documentId; }

        public String getOutputKey() { return outputKey; }
        public void setOutputKey(String outputKey) { this.outputKey = outputKey; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StepCheckpointId other)) return false;
            return Objects.equals(documentId, other.documentId)
                && Objects.equals(outputKey, other.outputKey);
        }

        @Override
        public int hashCode() { return Objects.hash(documentId, outputKey); }
    }
}
