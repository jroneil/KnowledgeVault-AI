package com.kva.document_service.search;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LlmCompletionRequest {
    String prompt;
    String modelName;
    Double temperature;
    Integer maxTokens;
}
