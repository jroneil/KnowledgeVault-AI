package com.kva.document_service.ingestion;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentEmbeddingServiceTests {

    @Test
    void generateAndPersistEmbeddingsProcessesChunksInBatches() {
        DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
        DocumentEmbeddingRepository repository = mock(DocumentEmbeddingRepository.class);
        DocumentEmbeddingService service = new DocumentEmbeddingService(client, repository, newProperties(2, 4));

        when(client.generateEmbeddings(any()))
                .thenReturn(successResult(2, 4))
                .thenReturn(successResult(1, 4));

        EmbeddingRunResult result = service.generateAndPersistEmbeddings(List.of(
                chunk(1L, "chunk-1"),
                chunk(2L, "chunk-2"),
                chunk(3L, "chunk-3")
        ));

        assertTrue(result.isSuccess());
        assertEquals(3, result.getEmbeddingCount());
        verify(client, times(2)).generateEmbeddings(any());
        verify(repository, times(2)).saveBatch(any());
    }

    @Test
    void generateAndPersistEmbeddingsFailsOnDimensionMismatch() {
        DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
        DocumentEmbeddingRepository repository = mock(DocumentEmbeddingRepository.class);
        DocumentEmbeddingService service = new DocumentEmbeddingService(client, repository, newProperties(2, 4));

        when(client.generateEmbeddings(any())).thenReturn(EmbeddingBatchResult.builder()
                .success(true)
                .retryable(false)
                .embeddings(List.of(
                        List.of(0.1d, 0.2d, 0.3d),
                        List.of(0.4d, 0.5d, 0.6d)
                ))
                .provider("ai-service")
                .modelName("nomic-embed-text")
                .modelVersion("v1")
                .build());

        EmbeddingRunResult result = service.generateAndPersistEmbeddings(List.of(
                chunk(1L, "chunk-1"),
                chunk(2L, "chunk-2")
        ));

        assertFalse(result.isSuccess());
        assertFalse(result.isRetryable());
        assertEquals("EMBEDDING_DIMENSION_MISMATCH", result.getErrorCode());
        verify(repository, never()).saveBatch(any());
    }

    @Test
    void generateAndPersistEmbeddingsReturnsRetryableFailureOnTimeout() {
        DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
        DocumentEmbeddingRepository repository = mock(DocumentEmbeddingRepository.class);
        DocumentEmbeddingService service = new DocumentEmbeddingService(client, repository, newProperties(2, 4));

        when(client.generateEmbeddings(any())).thenReturn(EmbeddingBatchResult.builder()
                .success(false)
                .retryable(true)
                .errorCode("EMBEDDING_TIMEOUT")
                .errorMessage("timed out")
                .provider("ai-service")
                .modelName("nomic-embed-text")
                .modelVersion("v1")
                .build());

        EmbeddingRunResult result = service.generateAndPersistEmbeddings(List.of(chunk(1L, "chunk-1")));

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertEquals("EMBEDDING_TIMEOUT", result.getErrorCode());
        verify(repository, never()).saveBatch(any());
    }

    @Test
    void generateAndPersistEmbeddingsStoresModelMetadata() {
        DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
        DocumentEmbeddingRepository repository = mock(DocumentEmbeddingRepository.class);
        DocumentEmbeddingService service = new DocumentEmbeddingService(client, repository, newProperties(5, 4));

        when(client.generateEmbeddings(any())).thenReturn(EmbeddingBatchResult.builder()
                .success(true)
                .retryable(false)
                .embeddings(List.of(List.of(0.1d, 0.2d, 0.3d, 0.4d)))
                .provider("ai-service")
                .modelName("embedding-model-x")
                .modelVersion("2026-06")
                .build());

        EmbeddingRunResult result = service.generateAndPersistEmbeddings(List.of(chunk(42L, "chunk-1")));

        ArgumentCaptor<List<DocumentEmbeddingRecord>> captor = ArgumentCaptor.forClass(List.class);
        assertTrue(result.isSuccess());
        verify(repository).saveBatch(captor.capture());
        DocumentEmbeddingRecord record = captor.getValue().get(0);
        assertEquals(42L, record.getChunkId());
        assertEquals("embedding-model-x", record.getModelName());
        assertEquals("2026-06", record.getModelVersion());
        assertEquals(4, record.getDimension());
    }

    private IngestionEmbeddingProperties newProperties(int batchSize, int dimension) {
        IngestionEmbeddingProperties properties = new IngestionEmbeddingProperties();
        properties.setEnabled(true);
        properties.setProvider("ai-service");
        properties.setModelName("nomic-embed-text");
        properties.setModelVersion("test-v1");
        properties.setDimension(dimension);
        properties.setBatchSize(batchSize);
        properties.setTimeoutMs(30_000);
        return properties;
    }

    private EmbeddingBatchResult successResult(int count, int dimension) {
        List<List<Double>> embeddings = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> java.util.Collections.nCopies(dimension, 0.01d))
                .map(List::copyOf)
                .toList();

        return EmbeddingBatchResult.builder()
                .success(true)
                .retryable(false)
                .embeddings(embeddings)
                .provider("ai-service")
                .modelName("nomic-embed-text")
                .modelVersion("test-v1")
                .build();
    }

    private DocumentChunk chunk(Long id, String content) {
        return DocumentChunk.builder()
                .id(id)
                .content(content)
                .build();
    }
}
