package com.kva.document_service.ingestion;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmbeddingRunResult {
    boolean success;
    int embeddingCount;
    String errorCode;
    String errorMessage;
    boolean retryable;

    public static EmbeddingRunResult success(int embeddingCount) {
        return EmbeddingRunResult.builder()
                .success(true)
                .embeddingCount(embeddingCount)
                .retryable(false)
                .build();
    }

    public static EmbeddingRunResult failure(String errorCode, String errorMessage, boolean retryable) {
        return EmbeddingRunResult.builder()
                .success(false)
                .embeddingCount(0)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .retryable(retryable)
                .build();
    }
}
