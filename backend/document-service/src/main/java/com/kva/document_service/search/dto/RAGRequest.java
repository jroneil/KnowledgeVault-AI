package com.kva.document_service.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for retrieval-augmented generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGRequest {
    private String query;
    private List<Long> documentIds;
    private int topK;
    private double similarityThreshold;
    private int maxTokens;
    private double temperature;
}