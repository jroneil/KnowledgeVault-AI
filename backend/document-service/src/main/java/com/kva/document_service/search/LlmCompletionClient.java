package com.kva.document_service.search;

public interface LlmCompletionClient {

    LlmCompletionResult complete(LlmCompletionRequest request);
}
