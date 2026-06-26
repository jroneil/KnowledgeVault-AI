package com.kva.document_service.search;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class QueryEmbeddingResult {
    boolean success;
    boolean retryable;
    String errorCode;
    String errorMessage;
    List<Double> embedding;
    String modelName;
    String modelVersion;
}
