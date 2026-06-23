package com.kva.document_service.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for semantic search using vector similarity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchRequest {
    private String query;
    private int limit;
    private double threshold;
    private List<Long> documentIds;
    private String modelName;
}