package com.kva.document_service.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGCitation {
    private String label;
    private Long documentId;
    private String documentTitle;
    private Long versionId;
    private Integer versionNumber;
    private String revision;
    private Integer pageNumber;
    private Integer sourcePageFrom;
    private Integer sourcePageTo;
    private Long chunkId;
    private Integer chunkIndex;
    private Integer rank;
    private Double similarityScore;
    private String sourceLabel;
}
