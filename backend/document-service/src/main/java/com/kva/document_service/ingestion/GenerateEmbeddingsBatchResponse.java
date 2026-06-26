package com.kva.document_service.ingestion;

import lombok.Data;

import java.util.List;

@Data
public class GenerateEmbeddingsBatchResponse {
    private List<List<Double>> embeddings;
    private String model;
    private Integer totalEmbeddings;
    private Boolean success;
}
