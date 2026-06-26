package com.kva.document_service.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionProcessingResponse {
    private boolean success;
    private Integer chunkCount;
    private Integer embeddingCount;
    private List<String> warningMessages;
    private String errorCode;
    private String errorMessage;
    private Boolean retryable;
}
