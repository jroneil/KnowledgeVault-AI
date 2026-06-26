package com.kva.document_service.ingestion;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DocumentEmbeddingRecord {
    Long chunkId;
    String modelName;
    String modelVersion;
    List<Double> embedding;
    Integer dimension;
}
