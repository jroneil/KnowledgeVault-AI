package com.kva.document_service.search;

import com.kva.document_service.ingestion.DocumentEmbeddingClient;
import com.kva.document_service.ingestion.EmbeddingBatchResult;
import com.kva.document_service.ingestion.GenerateEmbeddingsBatchRequest;
import com.kva.document_service.ingestion.IngestionEmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QueryEmbeddingService {

    private final DocumentEmbeddingClient documentEmbeddingClient;
    private final IngestionEmbeddingProperties ingestionEmbeddingProperties;

    public QueryEmbeddingResult generate(String queryText, String requestedModelName) {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }

        String modelName = hasText(requestedModelName)
                ? requestedModelName
                : ingestionEmbeddingProperties.getModelName();

        EmbeddingBatchResult batchResult = documentEmbeddingClient.generateEmbeddings(
                GenerateEmbeddingsBatchRequest.builder()
                        .texts(List.of(queryText))
                        .model(modelName)
                        .build()
        );

        if (!batchResult.isSuccess()) {
            return QueryEmbeddingResult.builder()
                    .success(false)
                    .retryable(batchResult.isRetryable())
                    .errorCode(batchResult.getErrorCode())
                    .errorMessage(batchResult.getErrorMessage())
                    .modelName(modelName)
                    .modelVersion(batchResult.getModelVersion())
                    .build();
        }

        List<List<Double>> embeddings = batchResult.getEmbeddings();
        if (embeddings == null || embeddings.size() != 1) {
            return QueryEmbeddingResult.builder()
                    .success(false)
                    .retryable(false)
                    .errorCode("QUERY_EMBEDDING_COUNT_MISMATCH")
                    .errorMessage("Embedding provider returned an unexpected number of query embeddings")
                    .modelName(resolveModelName(batchResult, modelName))
                    .modelVersion(batchResult.getModelVersion())
                    .build();
        }

        List<Double> embedding = embeddings.get(0);
        if (embedding == null || embedding.size() != ingestionEmbeddingProperties.getDimension()) {
            return QueryEmbeddingResult.builder()
                    .success(false)
                    .retryable(false)
                    .errorCode("QUERY_EMBEDDING_DIMENSION_MISMATCH")
                    .errorMessage("Query embedding dimension "
                            + (embedding == null ? 0 : embedding.size())
                            + " does not match expected dimension "
                            + ingestionEmbeddingProperties.getDimension())
                    .modelName(resolveModelName(batchResult, modelName))
                    .modelVersion(batchResult.getModelVersion())
                    .build();
        }

        return QueryEmbeddingResult.builder()
                .success(true)
                .retryable(false)
                .embedding(embedding)
                .modelName(resolveModelName(batchResult, modelName))
                .modelVersion(batchResult.getModelVersion())
                .build();
    }

    private String resolveModelName(EmbeddingBatchResult batchResult, String fallbackModelName) {
        return hasText(batchResult.getModelName()) ? batchResult.getModelName() : fallbackModelName;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
