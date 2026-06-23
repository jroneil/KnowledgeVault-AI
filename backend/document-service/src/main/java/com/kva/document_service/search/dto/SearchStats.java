package com.kva.document_service.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Statistics about the search index.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchStats {
    private long totalDocuments;
    private long totalChunks;
    private long totalEmbeddings;
    private List<String> embeddingModels;
    private List<String> llmModels;
}