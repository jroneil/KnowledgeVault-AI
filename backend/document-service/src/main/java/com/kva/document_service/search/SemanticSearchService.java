package com.kva.document_service.search;

import com.kva.document_service.search.dto.ChunkResult;
import com.kva.document_service.search.dto.SemanticSearchRequest;
import com.kva.document_service.search.dto.SemanticSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final QueryEmbeddingService queryEmbeddingService;
    private final SemanticRetrievalService semanticRetrievalService;

    public SemanticSearchResponse search(SemanticSearchRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }

        long startedAt = System.nanoTime();

        QueryEmbeddingResult queryEmbeddingResult = queryEmbeddingService.generate(
                request.getQuery(),
                request.getModelName()
        );

        if (!queryEmbeddingResult.isSuccess()) {
            throw new IllegalStateException(
                    queryEmbeddingResult.getErrorCode() + ": " + queryEmbeddingResult.getErrorMessage()
            );
        }

        List<SemanticRetrievalResult> retrieved = semanticRetrievalService.retrieve(
                SemanticRetrievalRequest.builder()
                        .queryEmbedding(queryEmbeddingResult.getEmbedding())
                        .topK(request.getLimit() > 0 ? request.getLimit() : null)
                        .modelName(queryEmbeddingResult.getModelName())
                        .build()
        );

        List<ChunkResult> results = retrieved.stream()
                .map(this::toChunkResult)
                .toList();

        return SemanticSearchResponse.builder()
                .query(request.getQuery())
                .totalResults(results.size())
                .results(results)
                .queryEmbeddingDimension(queryEmbeddingResult.getEmbedding().size())
                .embeddingModel(queryEmbeddingResult.getModelName())
                .embeddingModelVersion(queryEmbeddingResult.getModelVersion())
                .searchTimeMs((System.nanoTime() - startedAt) / 1_000_000.0d)
                .build();
    }

    private ChunkResult toChunkResult(SemanticRetrievalResult result) {
        return ChunkResult.builder()
                .chunkId(result.getChunkId())
                .documentId(result.getDocumentId())
                .versionId(result.getVersionId())
                .chunkIndex(result.getChunkIndex())
                .content(result.getContent())
                .pageNumber(result.getPageNumber())
                .sectionName(result.getSectionName())
                .tokenCount(result.getTokenCount())
                .similarityScore(result.getSimilarityScore())
                .build();
    }
}
