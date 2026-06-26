package com.kva.document_service.search;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RagPromptPackage {
    String question;
    String prompt;
    List<RagPromptContextItem> contexts;
    int totalContexts;
    int totalEstimatedTokens;
    boolean truncated;
}
