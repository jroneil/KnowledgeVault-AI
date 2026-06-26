package com.kva.document_service.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from RAG query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGResponse {
    private String query;
    private String answer;
    private List<RAGContext> contexts;
    private List<RAGCitation> citations;
    private int totalContexts;
    private String modelUsed;
    private String embeddingModel;
    private double processingTimeMs;
}
