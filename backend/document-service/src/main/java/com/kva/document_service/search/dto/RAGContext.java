package com.kva.document_service.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context retrieved from search for RAG.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGContext {
    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Double similarityScore;
}