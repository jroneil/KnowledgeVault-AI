package com.kva.document_service.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result from semantic search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkResult {
    private Long chunkId;
    private Long documentId;
    private Long versionId;
    private Integer chunkIndex;
    private String content;
    private Integer pageNumber;
    private String sectionName;
    private Integer tokenCount;
    private Double similarityScore;
    private String createdAt;
}