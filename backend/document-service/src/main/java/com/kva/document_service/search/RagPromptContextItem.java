package com.kva.document_service.search;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RagPromptContextItem {
    int rank;
    Long chunkId;
    Long documentId;
    String documentTitle;
    Long versionId;
    Integer versionNumber;
    Integer chunkIndex;
    String revision;
    String product;
    String category;
    List<String> tags;
    Integer pageNumber;
    Integer sourcePageFrom;
    Integer sourcePageTo;
    Double similarityScore;
    Integer estimatedTokens;
    String content;
    String sourceLabel;
}
