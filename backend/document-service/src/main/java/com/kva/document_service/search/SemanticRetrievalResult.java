package com.kva.document_service.search;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SemanticRetrievalResult {
    Long chunkId;
    Long documentId;
    Long versionId;
    Integer versionNumber;
    Integer chunkIndex;
    String content;
    Integer pageNumber;
    Integer sourcePageFrom;
    Integer sourcePageTo;
    String sectionName;
    Integer tokenCount;
    Double similarityScore;
}
