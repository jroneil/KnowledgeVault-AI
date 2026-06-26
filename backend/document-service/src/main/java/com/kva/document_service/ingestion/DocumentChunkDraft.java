package com.kva.document_service.ingestion;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocumentChunkDraft {
    Integer chunkIndex;
    String content;
    Integer sourcePageFrom;
    Integer sourcePageTo;
    Integer tokenCount;
}
