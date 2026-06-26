package com.kva.document_service.search;

import com.kva.document_service.search.dto.RAGRequest;
import com.kva.document_service.search.dto.RAGResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagAnswerServiceTests {

    @Test
    void answerGeneratesLlmResponseAndPreservesSourceMetadata() {
        QueryEmbeddingService queryEmbeddingService = mock(QueryEmbeddingService.class);
        SemanticRetrievalService semanticRetrievalService = mock(SemanticRetrievalService.class);
        RagPromptAssemblyService ragPromptAssemblyService = mock(RagPromptAssemblyService.class);
        LlmCompletionClient llmCompletionClient = mock(LlmCompletionClient.class);
        RagCitationService ragCitationService = new RagCitationService();
        RagAnswerService service = new RagAnswerService(
                queryEmbeddingService,
                semanticRetrievalService,
                ragPromptAssemblyService,
                llmCompletionClient,
                ragCitationService
        );
        ReflectionTestUtils.setField(service, "defaultLlmModel", "llama3.1");

        when(queryEmbeddingService.generate("How often?", null)).thenReturn(QueryEmbeddingResult.builder()
                .success(true)
                .embedding(List.of(0.1d, 0.2d, 0.3d, 0.4d))
                .modelName("nomic-embed-text")
                .build());
        when(semanticRetrievalService.retrieve(any())).thenReturn(List.of(
                SemanticRetrievalResult.builder()
                        .chunkId(11L)
                        .documentId(22L)
                        .versionId(33L)
                        .versionNumber(2)
                        .chunkIndex(0)
                        .pageNumber(7)
                        .content("Every 500 hours.")
                        .similarityScore(0.93d)
                        .build()
        ));
        RagPromptPackage promptPackage = RagPromptPackage.builder()
                .question("How often?")
                .prompt("assembled prompt")
                .contexts(List.of(RagPromptContextItem.builder()
                        .rank(1)
                        .chunkId(11L)
                        .documentId(22L)
                        .documentTitle("Compressor Manual")
                        .versionId(33L)
                        .versionNumber(2)
                        .chunkIndex(0)
                        .revision("B")
                        .product("Compressor X")
                        .category("Maintenance")
                        .tags(List.of("maintenance", "compressor"))
                        .pageNumber(7)
                        .sourcePageFrom(7)
                        .sourcePageTo(7)
                        .similarityScore(0.93d)
                        .content("Every 500 hours.")
                        .sourceLabel("Document: Compressor Manual | Version: 2 | Revision: B | Page: 7")
                        .build()))
                .totalContexts(1)
                .totalEstimatedTokens(10)
                .truncated(false)
                .build();
        when(ragPromptAssemblyService.assemble(eq("How often?"), any())).thenReturn(promptPackage);
        when(llmCompletionClient.complete(any())).thenReturn(LlmCompletionResult.builder()
                .success(true)
                .answer("The maintenance interval is every 500 hours.")
                .modelName("llama3.1")
                .build());

        RAGResponse response = service.answer(RAGRequest.builder()
                .query("How often?")
                .topK(3)
                .maxTokens(400)
                .temperature(0.2d)
                .build());

        assertEquals("The maintenance interval is every 500 hours.", response.getAnswer());
        assertEquals(1, response.getTotalContexts());
        assertEquals("llama3.1", response.getModelUsed());
        assertEquals("nomic-embed-text", response.getEmbeddingModel());
        assertEquals("Compressor Manual", response.getContexts().get(0).getDocumentTitle());
        assertEquals("B", response.getContexts().get(0).getRevision());
        assertEquals(7, response.getContexts().get(0).getPageNumber());
        assertEquals(1, response.getCitations().size());
        assertTrue(response.getCitations().get(0).getLabel().contains("Compressor Manual"));
        assertTrue(response.getCitations().get(0).getLabel().contains("v2"));
        assertTrue(response.getCitations().get(0).getLabel().contains("rev B"));
        assertTrue(response.getCitations().get(0).getLabel().contains("p. 7"));
        assertEquals("Document: Compressor Manual | Version: 2 | Revision: B | Page: 7",
                response.getContexts().get(0).getSourceLabel());
        verify(llmCompletionClient).complete(eq(LlmCompletionRequest.builder()
                .prompt("assembled prompt")
                .modelName("llama3.1")
                .temperature(0.2d)
                .maxTokens(400)
                .build()));
    }

    @Test
    void answerReturnsFallbackWhenNoContextExists() {
        QueryEmbeddingService queryEmbeddingService = mock(QueryEmbeddingService.class);
        SemanticRetrievalService semanticRetrievalService = mock(SemanticRetrievalService.class);
        RagPromptAssemblyService ragPromptAssemblyService = mock(RagPromptAssemblyService.class);
        LlmCompletionClient llmCompletionClient = mock(LlmCompletionClient.class);
        RagCitationService ragCitationService = new RagCitationService();
        RagAnswerService service = new RagAnswerService(
                queryEmbeddingService,
                semanticRetrievalService,
                ragPromptAssemblyService,
                llmCompletionClient,
                ragCitationService
        );
        ReflectionTestUtils.setField(service, "defaultLlmModel", "llama3.1");

        when(queryEmbeddingService.generate("Unknown", null)).thenReturn(QueryEmbeddingResult.builder()
                .success(true)
                .embedding(List.of(0.1d, 0.2d, 0.3d, 0.4d))
                .modelName("nomic-embed-text")
                .build());
        when(semanticRetrievalService.retrieve(any())).thenReturn(List.of());
        when(ragPromptAssemblyService.assemble(eq("Unknown"), any())).thenReturn(RagPromptPackage.builder()
                .question("Unknown")
                .prompt("Question:\nUnknown\n\nRetrieved Context:\n(no relevant context retrieved)")
                .contexts(List.of())
                .totalContexts(0)
                .totalEstimatedTokens(0)
                .truncated(false)
                .build());

        RAGResponse response = service.answer(RAGRequest.builder().query("Unknown").build());

        assertEquals(0, response.getTotalContexts());
        assertEquals(null, response.getAnswer());
        assertEquals(List.of(), response.getCitations());
        verify(llmCompletionClient, never()).complete(any());
    }

    @Test
    void answerRaisesFailureWhenLlmTimesOut() {
        QueryEmbeddingService queryEmbeddingService = mock(QueryEmbeddingService.class);
        SemanticRetrievalService semanticRetrievalService = mock(SemanticRetrievalService.class);
        RagPromptAssemblyService ragPromptAssemblyService = mock(RagPromptAssemblyService.class);
        LlmCompletionClient llmCompletionClient = mock(LlmCompletionClient.class);
        RagCitationService ragCitationService = new RagCitationService();
        RagAnswerService service = new RagAnswerService(
                queryEmbeddingService,
                semanticRetrievalService,
                ragPromptAssemblyService,
                llmCompletionClient,
                ragCitationService
        );
        ReflectionTestUtils.setField(service, "defaultLlmModel", "llama3.1");

        when(queryEmbeddingService.generate("Timeout?", null)).thenReturn(QueryEmbeddingResult.builder()
                .success(true)
                .embedding(List.of(0.1d, 0.2d, 0.3d, 0.4d))
                .modelName("nomic-embed-text")
                .build());
        when(semanticRetrievalService.retrieve(any())).thenReturn(List.of(SemanticRetrievalResult.builder()
                .chunkId(11L)
                .documentId(22L)
                .content("context")
                .build()));
        when(ragPromptAssemblyService.assemble(eq("Timeout?"), any())).thenReturn(RagPromptPackage.builder()
                .question("Timeout?")
                .prompt("assembled prompt")
                .contexts(List.of(RagPromptContextItem.builder()
                        .rank(1)
                        .chunkId(11L)
                        .documentId(22L)
                        .chunkIndex(0)
                        .content("context")
                        .sourceLabel("Document ID: 22 | Chunk: null | Document ID: 22")
                        .build()))
                .totalContexts(1)
                .totalEstimatedTokens(1)
                .truncated(false)
                .build());
        when(llmCompletionClient.complete(any())).thenReturn(LlmCompletionResult.builder()
                .success(false)
                .retryable(true)
                .errorCode("LLM_TIMEOUT")
                .errorMessage("timed out")
                .modelName("llama3.1")
                .build());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.answer(RAGRequest.builder().query("Timeout?").build())
        );

        assertEquals("LLM_TIMEOUT: timed out", exception.getMessage());
    }

    @Test
    void citationsDeduplicateRepeatedSourcesAndPreserveTraceability() {
        RagCitationService ragCitationService = new RagCitationService();

        var citations = ragCitationService.formatCitations(List.of(
                com.kva.document_service.search.dto.RAGContext.builder()
                        .chunkId(11L)
                        .documentId(22L)
                        .documentTitle("Manual A")
                        .versionId(33L)
                        .versionNumber(2)
                        .revision("B")
                        .pageNumber(7)
                        .chunkIndex(0)
                        .similarityScore(0.93d)
                        .sourceLabel("Document: Manual A | Version: 2 | Revision: B | Page: 7")
                        .build(),
                com.kva.document_service.search.dto.RAGContext.builder()
                        .chunkId(11L)
                        .documentId(22L)
                        .documentTitle("Manual A")
                        .versionId(33L)
                        .versionNumber(2)
                        .revision("B")
                        .pageNumber(7)
                        .chunkIndex(0)
                        .similarityScore(0.91d)
                        .sourceLabel("Document: Manual A | Version: 2 | Revision: B | Page: 7")
                        .build(),
                com.kva.document_service.search.dto.RAGContext.builder()
                        .documentId(55L)
                        .documentTitle(null)
                        .versionId(null)
                        .sourcePageFrom(2)
                        .sourcePageTo(3)
                        .chunkIndex(4)
                        .similarityScore(0.88d)
                        .sourceLabel("Document ID: 55 | Pages: 2-3 | Chunk: 4")
                        .build()
        ));

        assertEquals(2, citations.size());
        assertTrue(citations.get(0).getLabel().contains("Manual A"));
        assertTrue(citations.get(0).getLabel().contains("chunk 11"));
        assertEquals("Document: Manual A | Version: 2 | Revision: B | Page: 7", citations.get(0).getSourceLabel());
        assertTrue(citations.get(1).getLabel().contains("Document 55"));
        assertTrue(citations.get(1).getLabel().contains("pp. 2-3"));
        assertEquals(55L, citations.get(1).getDocumentId());
    }
}
