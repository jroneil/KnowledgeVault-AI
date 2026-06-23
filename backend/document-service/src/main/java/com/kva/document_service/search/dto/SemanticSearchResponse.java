package com.kva.document_service.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from semantic search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchResponse {
    private String query;
    private int totalResults;
    private List<ChunkResult> results;
    private int queryEmbeddingDimension;
    private double searchTimeMs;
}