package com.kva.document_service.ingestion;

public interface DocumentEmbeddingClient {

    EmbeddingBatchResult generateEmbeddings(GenerateEmbeddingsBatchRequest request);
}
