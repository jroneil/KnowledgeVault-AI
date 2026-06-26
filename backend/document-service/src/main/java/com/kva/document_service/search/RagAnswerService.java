package com.kva.document_service.search;

import com.kva.document_service.search.dto.RAGContext;
import com.kva.document_service.search.dto.RAGCitation;
import com.kva.document_service.search.dto.RAGRequest;
import com.kva.document_service.search.dto.RAGResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private final QueryEmbeddingService queryEmbeddingService;
    private final SemanticRetrievalService semanticRetrievalService;
    private final RagPromptAssemblyService ragPromptAssemblyService;
    private final LlmCompletionClient llmCompletionClient;
    private final RagCitationService ragCitationService;

    @Value("${ai-service.llm-model:}")
    private String defaultLlmModel;

    public RAGResponse answer(RAGRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }

        long startedAt = System.nanoTime();
        QueryEmbeddingResult queryEmbeddingResult = queryEmbeddingService.generate(request.getQuery(), null);
        if (!queryEmbeddingResult.isSuccess()) {
            throw new IllegalStateException(queryEmbeddingResult.getErrorCode() + ": " + queryEmbeddingResult.getErrorMessage());
        }

        List<SemanticRetrievalResult> retrieved = semanticRetrievalService.retrieve(
                SemanticRetrievalRequest.builder()
                        .queryEmbedding(queryEmbeddingResult.getEmbedding())
                        .topK(request.getTopK() > 0 ? request.getTopK() : null)
                        .modelName(queryEmbeddingResult.getModelName())
                        .build()
        );

        RagPromptPackage promptPackage = ragPromptAssemblyService.assemble(request.getQuery(), retrieved);
        List<RAGContext> contexts = promptPackage.getContexts().stream()
                .map(this::toRagContext)
                .toList();
        List<RAGCitation> citations = ragCitationService.formatCitations(contexts);

        if (contexts.isEmpty()) {
            return RAGResponse.builder()
                    .query(request.getQuery())
                    .answer(null)
                    .contexts(contexts)
                    .citations(List.of())
                    .totalContexts(0)
                    .modelUsed(normalize(defaultLlmModel))
                    .embeddingModel(queryEmbeddingResult.getModelName())
                    .processingTimeMs((System.nanoTime() - startedAt) / 1_000_000.0d)
                    .build();
        }

        LlmCompletionResult llmResult = llmCompletionClient.complete(
                LlmCompletionRequest.builder()
                        .prompt(promptPackage.getPrompt())
                        .modelName(normalize(defaultLlmModel))
                        .temperature(request.getTemperature() > 0 ? request.getTemperature() : 0.0d)
                        .maxTokens(request.getMaxTokens() > 0 ? request.getMaxTokens() : 1000)
                        .build()
        );
        if (!llmResult.isSuccess()) {
            throw new IllegalStateException(llmResult.getErrorCode() + ": " + llmResult.getErrorMessage());
        }

        return RAGResponse.builder()
                .query(request.getQuery())
                .answer(llmResult.getAnswer())
                .contexts(contexts)
                .citations(citations)
                .totalContexts(contexts.size())
                .modelUsed(llmResult.getModelName())
                .embeddingModel(queryEmbeddingResult.getModelName())
                .processingTimeMs((System.nanoTime() - startedAt) / 1_000_000.0d)
                .build();
    }

    private RAGContext toRagContext(RagPromptContextItem contextItem) {
        return RAGContext.builder()
                .chunkId(contextItem.getChunkId())
                .documentId(contextItem.getDocumentId())
                .documentTitle(contextItem.getDocumentTitle())
                .versionId(contextItem.getVersionId())
                .versionNumber(contextItem.getVersionNumber())
                .revision(contextItem.getRevision())
                .product(contextItem.getProduct())
                .category(contextItem.getCategory())
                .tags(contextItem.getTags())
                .chunkIndex(contextItem.getChunkIndex())
                .content(contextItem.getContent())
                .pageNumber(contextItem.getPageNumber())
                .sourcePageFrom(contextItem.getSourcePageFrom())
                .sourcePageTo(contextItem.getSourcePageTo())
                .sourceLabel(contextItem.getSourceLabel())
                .similarityScore(contextItem.getSimilarityScore())
                .build();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
