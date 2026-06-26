package com.kva.document_service.ingestion.dto;

import com.kva.document_service.ingestion.IngestionJob;
import com.kva.document_service.ingestion.IngestionJobStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class IngestionJobResponse {
    Long id;
    Long documentId;
    Long versionId;
    IngestionJobStatus status;
    Integer progressPercent;
    Integer attemptCount;
    String embeddingModel;
    String llmModel;
    Integer chunkCount;
    Integer embeddingCount;
    String errorCode;
    String errorMessage;
    LocalDateTime nextAttemptAt;
    Boolean retryable;
    String lastErrorCode;
    String lastErrorMessage;
    Boolean cancellationRequested;
    List<IngestionWarningResponse> warnings;
    LocalDateTime createdAt;
    LocalDateTime startedAt;
    LocalDateTime completedAt;
    LocalDateTime updatedAt;

    public static IngestionJobResponse fromEntity(IngestionJob job) {
        return fromEntity(job, List.of());
    }

    public static IngestionJobResponse fromEntity(IngestionJob job, List<IngestionWarningResponse> warnings) {
        return IngestionJobResponse.builder()
                .id(job.getId())
                .documentId(job.getDocumentId())
                .versionId(job.getVersionId())
                .status(job.getStatus())
                .progressPercent(job.getProgressPercent())
                .attemptCount(job.getAttemptCount())
                .embeddingModel(job.getEmbeddingModel())
                .llmModel(job.getLlmModel())
                .chunkCount(job.getChunkCount())
                .embeddingCount(job.getEmbeddingCount())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .nextAttemptAt(job.getNextAttemptAt())
                .retryable(job.getRetryable())
                .lastErrorCode(job.getLastErrorCode())
                .lastErrorMessage(job.getLastErrorMessage())
                .cancellationRequested(job.getCancellationRequested())
                .warnings(warnings)
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
