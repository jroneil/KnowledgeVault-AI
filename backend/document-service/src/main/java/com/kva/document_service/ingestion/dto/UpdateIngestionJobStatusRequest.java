package com.kva.document_service.ingestion.dto;

import com.kva.document_service.ingestion.IngestionJobStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIngestionJobStatusRequest {

    @NotNull(message = "Status is required")
    private IngestionJobStatus status;

    @Min(value = 0, message = "Progress percent must be at least 0")
    @Max(value = 100, message = "Progress percent must be at most 100")
    private Integer progressPercent;

    @Min(value = 0, message = "Attempt count must be at least 0")
    private Integer attemptCount;

    @Size(max = 255, message = "Embedding model must not exceed 255 characters")
    private String embeddingModel;

    @Size(max = 255, message = "LLM model must not exceed 255 characters")
    private String llmModel;

    @Min(value = 0, message = "Chunk count must be at least 0")
    private Integer chunkCount;

    @Min(value = 0, message = "Embedding count must be at least 0")
    private Integer embeddingCount;

    @Size(max = 100, message = "Error code must not exceed 100 characters")
    private String errorCode;

    @Size(max = 4000, message = "Error message must not exceed 4000 characters")
    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
