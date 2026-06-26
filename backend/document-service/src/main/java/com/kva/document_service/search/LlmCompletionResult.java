package com.kva.document_service.search;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LlmCompletionResult {
    boolean success;
    boolean retryable;
    String errorCode;
    String errorMessage;
    String answer;
    String modelName;
}
