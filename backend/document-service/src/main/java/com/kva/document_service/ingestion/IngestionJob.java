package com.kva.document_service.ingestion;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IngestionJobStatus status;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "embedding_model", length = 255)
    private String embeddingModel;

    @Column(name = "llm_model", length = 255)
    private String llmModel;

    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount;

    @Column(name = "embedding_count", nullable = false)
    private Integer embeddingCount;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "retryable", nullable = false)
    private Boolean retryable;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "cancellation_requested", nullable = false)
    private Boolean cancellationRequested;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
