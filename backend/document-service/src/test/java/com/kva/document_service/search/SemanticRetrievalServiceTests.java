package com.kva.document_service.search;

import com.kva.document_service.ingestion.IngestionEmbeddingProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticRetrievalServiceTests {

    @Test
    void retrieveUsesConfiguredTopKAndDefaultModelWhenRequestDoesNotOverride() {
        SemanticRetrievalRepository repository = mock(SemanticRetrievalRepository.class);
        SemanticRetrievalProperties properties = new SemanticRetrievalProperties();
        properties.setTopK(7);
        IngestionEmbeddingProperties embeddingProperties = newEmbeddingProperties();
        SemanticRetrievalService service = new SemanticRetrievalService(repository, properties, embeddingProperties);

        when(repository.findTopSimilarChunks(any(), eq("nomic-embed-text"), eq(4), eq(7)))
                .thenReturn(List.of(SemanticRetrievalResult.builder().chunkId(1L).build()));

        List<SemanticRetrievalResult> results = service.retrieve(SemanticRetrievalRequest.builder()
                .queryEmbedding(List.of(0.1d, 0.2d, 0.3d, 0.4d))
                .build());

        assertEquals(1, results.size());
        verify(repository).findTopSimilarChunks(
                List.of(0.1d, 0.2d, 0.3d, 0.4d),
                "nomic-embed-text",
                4,
                7
        );
    }

    @Test
    void retrieveUsesRequestTopKAndModelOverride() {
        SemanticRetrievalRepository repository = mock(SemanticRetrievalRepository.class);
        SemanticRetrievalProperties properties = new SemanticRetrievalProperties();
        properties.setTopK(5);
        IngestionEmbeddingProperties embeddingProperties = newEmbeddingProperties();
        SemanticRetrievalService service = new SemanticRetrievalService(repository, properties, embeddingProperties);

        when(repository.findTopSimilarChunks(any(), eq("custom-model"), eq(4), eq(3)))
                .thenReturn(Collections.emptyList());

        List<SemanticRetrievalResult> results = service.retrieve(SemanticRetrievalRequest.builder()
                .queryEmbedding(List.of(0.1d, 0.2d, 0.3d, 0.4d))
                .modelName("custom-model")
                .topK(3)
                .build());

        assertEquals(0, results.size());
        verify(repository).findTopSimilarChunks(
                List.of(0.1d, 0.2d, 0.3d, 0.4d),
                "custom-model",
                4,
                3
        );
    }

    @Test
    void retrieveRejectsWrongEmbeddingDimension() {
        SemanticRetrievalRepository repository = mock(SemanticRetrievalRepository.class);
        SemanticRetrievalProperties properties = new SemanticRetrievalProperties();
        IngestionEmbeddingProperties embeddingProperties = newEmbeddingProperties();
        SemanticRetrievalService service = new SemanticRetrievalService(repository, properties, embeddingProperties);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.retrieve(SemanticRetrievalRequest.builder()
                        .queryEmbedding(List.of(0.1d, 0.2d))
                        .build())
        );

        assertEquals("queryEmbedding dimension 2 does not match expected dimension 4", exception.getMessage());
    }

    private IngestionEmbeddingProperties newEmbeddingProperties() {
        IngestionEmbeddingProperties properties = new IngestionEmbeddingProperties();
        properties.setModelName("nomic-embed-text");
        properties.setDimension(4);
        return properties;
    }
}
