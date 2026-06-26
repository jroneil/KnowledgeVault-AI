package com.kva.document_service.search;

import com.kva.document_service.search.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for semantic search and RAG functionality.
 * Provides AI-powered search capabilities using vector embeddings.
 */
@RestController
@RequestMapping("/api/v1/semantic-search")
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;
    private final RagAnswerService ragAnswerService;
    private final AISearchClient aiSearchClient;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public SemanticSearchResponse semanticSearch(@RequestBody SemanticSearchRequest request) {
        log.info("Semantic search request: query='{}', limit={}, threshold={}", 
            request.getQuery(), request.getLimit(), request.getThreshold());
        SemanticSearchResponse response = semanticSearchService.search(request);
        log.info("Semantic search completed: {} results found", response.getTotalResults());
        return response;
    }

    @PostMapping("/rag")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public RAGResponse ragQuery(@RequestBody RAGRequest request) {
        log.info("RAG query request: query='{}', topK={}", request.getQuery(), request.getTopK());
        RAGResponse response = ragAnswerService.answer(request);
        log.info("RAG query completed: {} contexts retrieved, {} characters in answer",
                response.getTotalContexts(),
                response.getAnswer() == null ? 0 : response.getAnswer().length());
        return response;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public SearchStats getSearchStats() {
        log.info("Getting search statistics");

        try {
            SearchStats stats = aiSearchClient.getSearchStats();
            log.info("Search stats: {} documents, {} chunks, {} embeddings", 
                stats.getTotalDocuments(), stats.getTotalChunks(), stats.getTotalEmbeddings());
            return stats;

        } catch (Exception e) {
            log.error("Error getting search stats: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get search stats: " + e.getMessage());
        }
    }
}
