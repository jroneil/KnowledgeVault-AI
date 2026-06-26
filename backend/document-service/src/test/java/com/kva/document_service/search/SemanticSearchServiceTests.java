package com.kva.document_service.search;

import com.kva.document_service.search.dto.SemanticSearchRequest;
import com.kva.document_service.search.dto.SemanticSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticSearchServiceTests {

    @Test
    void searchEmbedsQueryTextAndReturnsRetrievedChunks() {
        QueryEmbeddingService queryEmbeddingService = mock(QueryEmbeddingService.class);
        SemanticRetrievalService semanticRetrievalService = mock(SemanticRetrievalService.class);
        SemanticSearchService service = new SemanticSearchService(queryEmbeddingService, semanticRetrievalService);

        when(queryEmbeddingService.generate("compressor maintenance", "nomic-embed-text"))
                .thenReturn(QueryEmbeddingResult.builder()
                        .success(true)
                        .embedding(List.of(0.1d, 0.2d, 0.3d, 0.4d))
                        .modelName("nomic-embed-text")
                        .modelVersion("2026-06")
                        .build());
        when(semanticRetrievalService.retrieve(any())).thenReturn(List.of(
                SemanticRetrievalResult.builder()
                        .chunkId(11L)
                        .documentId(22L)
                        .versionId(33L)
                        .versionNumber(2)
                        .chunkIndex(4)
                        .content("compressor maintenance interval")
                        .pageNumber(7)
                        .sectionName("Maintenance")
                        .tokenCount(81)
                        .similarityScore(0.93d)
                        .build()
        ));

        SemanticSearchResponse response = service.search(SemanticSearchRequest.builder()
                .query("compressor maintenance")
                .limit(3)
                .modelName("nomic-embed-text")
                .build());

        assertEquals("compressor maintenance", response.getQuery());
        assertEquals(1, response.getTotalResults());
        assertEquals(4, response.getQueryEmbeddingDimension());
        assertEquals("nomic-embed-text", response.getEmbeddingModel());
        assertEquals("2026-06", response.getEmbeddingModelVersion());
        assertEquals(7, response.getResults().get(0).getPageNumber());
        assertEquals(0.93d, response.getResults().get(0).getSimilarityScore());
        assertTrue(response.getSearchTimeMs() >= 0.0d);
        verify(queryEmbeddingService).generate("compressor maintenance", "nomic-embed-text");
        verify(semanticRetrievalService).retrieve(eq(SemanticRetrievalRequest.builder()
                .queryEmbedding(List.of(0.1d, 0.2d, 0.3d, 0.4d))
                .topK(3)
                .modelName("nomic-embed-text")
                .build()));
    }

    @Test
    void searchReturnsEmptyResultsWhenNothingMatches() {
        QueryEmbeddingService queryEmbeddingService = mock(QueryEmbeddingService.class);
        SemanticRetrievalService semanticRetrievalService = mock(SemanticRetrievalService.class);
        SemanticSearchService service = new SemanticSearchService(queryEmbeddingService, semanticRetrievalService);

        when(queryEmbeddingService.generate("no match", null))
                .thenReturn(QueryEmbeddingResult.builder()
                        .success(true)
                        .embedding(List.of(0.1d, 0.2d, 0.3d, 0.4d))
                        .modelName("nomic-embed-text")
                        .modelVersion("2026-06")
                        .build());
        when(semanticRetrievalService.retrieve(any())).thenReturn(List.of());

        SemanticSearchResponse response = service.search(SemanticSearchRequest.builder()
                .query("no match")
                .build());

        assertEquals(0, response.getTotalResults());
        assertEquals(List.of(), response.getResults());
    }

    @Test
    void searchRaisesFailureWhenQueryEmbeddingGenerationFails() {
        QueryEmbeddingService queryEmbeddingService = mock(QueryEmbeddingService.class);
        SemanticRetrievalService semanticRetrievalService = mock(SemanticRetrievalService.class);
        SemanticSearchService service = new SemanticSearchService(queryEmbeddingService, semanticRetrievalService);

        when(queryEmbeddingService.generate("timeout case", null))
                .thenReturn(QueryEmbeddingResult.builder()
                        .success(false)
                        .retryable(true)
                        .errorCode("EMBEDDING_TIMEOUT")
                        .errorMessage("timed out")
                        .modelName("nomic-embed-text")
                        .build());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.search(SemanticSearchRequest.builder()
                        .query("timeout case")
                        .build())
        );

        assertEquals("EMBEDDING_TIMEOUT: timed out", exception.getMessage());
    }
}
