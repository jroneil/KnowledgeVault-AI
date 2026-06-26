package com.kva.document_service.search;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SemanticRetrievalRequest {
    List<Double> queryEmbedding;
    Integer topK;
    String modelName;
}
