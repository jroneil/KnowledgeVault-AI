package com.kva.document_service.ingestion;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class EmbeddingBatchResult {
    boolean success;
    boolean retryable;
    String errorCode;
    String errorMessage;
    List<List<Double>> embeddings;
    String provider;
    String modelName;
    String modelVersion;
}
