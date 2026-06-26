package com.kva.document_service.search;

import com.kva.document_service.ingestion.DocumentEmbeddingClient;
import com.kva.document_service.ingestion.EmbeddingBatchResult;
import com.kva.document_service.ingestion.GenerateEmbeddingsBatchRequest;
import com.kva.document_service.ingestion.IngestionEmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryEmbeddingServiceTests {

    @Test
    void generateCreatesQueryEmbeddingUsingConfiguredModel() {
        DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
        QueryEmbeddingService service = new QueryEmbeddingService(client, embeddingProperties());

        when(client.generateEmbeddings(any())).thenReturn(EmbeddingBatchResult.builder()
                .success(true)
                .retryable(false)
                .embeddings(List.of(List.of(0.1d, 0.2d, 0.3d, 0.4d)))
                .modelName("nomic-embed-text")
                .modelVersion("2026-06")
                .build());

        QueryEmbeddingResult result = service.generate("find turbine docs", null);
        ArgumentCaptor<GenerateEmbeddingsBatchRequest> captor = ArgumentCaptor.forClass(GenerateEmbeddingsBatchRequest.class);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getEmbedding().size());
        assertEquals("nomic-embed-text", result.getModelName());
        assertEquals("2026-06", result.getModelVersion());
        verify(client).generateEmbeddings(captor.capture());
        assertEquals(List.of("find turbine docs"), captor.getValue().getTexts());
        assertEquals("nomic-embed-text", captor.getValue().getModel());
    }

    @Test
    void generateRejectsDimensionMismatch() {
        DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
        QueryEmbeddingService service = new QueryEmbeddingService(client, embeddingProperties());

        when(client.generateEmbeddings(any())).thenReturn(EmbeddingBatchResult.builder()
                .success(true)
                .retryable(false)
                .embeddings(List.of(List.of(0.1d, 0.2d)))
                .modelName("nomic-embed-text")
                .modelVersion("2026-06")
                .build());

        QueryEmbeddingResult result = service.generate("find turbine docs", null);

        assertFalse(result.isSuccess());
        assertEquals("QUERY_EMBEDDING_DIMENSION_MISMATCH", result.getErrorCode());
    }

    @Test
    void generatePreservesRetryableTimeoutFailure() {
        DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
        QueryEmbeddingService service = new QueryEmbeddingService(client, embeddingProperties());

        when(client.generateEmbeddings(any())).thenReturn(EmbeddingBatchResult.builder()
                .success(false)
                .retryable(true)
                .errorCode("EMBEDDING_TIMEOUT")
                .errorMessage("timed out")
                .modelName("nomic-embed-text")
                .modelVersion("2026-06")
                .build());

        QueryEmbeddingResult result = service.generate("find turbine docs", null);

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertEquals("EMBEDDING_TIMEOUT", result.getErrorCode());
    }

    @Test
    void generateRejectsBlankQuery() {
        DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
        QueryEmbeddingService service = new QueryEmbeddingService(client, embeddingProperties());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.generate("  ", null)
        );

        assertEquals("query is required", exception.getMessage());
    }

    private IngestionEmbeddingProperties embeddingProperties() {
        IngestionEmbeddingProperties properties = new IngestionEmbeddingProperties();
        properties.setModelName("nomic-embed-text");
        properties.setModelVersion("2026-06");
        properties.setDimension(4);
        return properties;
    }
}
