package com.kva.document_service.search;

import com.kva.document_service.documents.Document;
import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.search.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for semantic search and RAG functionality.
 * Provides AI-powered search capabilities using vector embeddings.
 */
@RestController
@RequestMapping("/api/v1/semantic-search")
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchController {

    private final AISearchClient aiSearchClient;
    private final DocumentRepository documentRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public SemanticSearchResponse semanticSearch(@RequestBody SemanticSearchRequest request) {
        log.info("Semantic search request: query='{}', limit={}, threshold={}", 
            request.getQuery(), request.getLimit(), request.getThreshold());

        try {
            // Call AI service for semantic search
            SemanticSearchResponse response = aiSearchClient.semanticSearch(request);

            // Enrich results with document metadata
            if (response.getResults() != null) {
                Set<Long> documentIds = response.getResults().stream()
                    .map(ChunkResult::getDocumentId)
                    .collect(Collectors.toSet());

                Map<Long, Document> documentMap = new HashMap<>();
                for (Long docId : documentIds) {
                    documentRepository.findById(docId).ifPresent(doc -> documentMap.put(docId, doc));
                }

                // Add document titles to results
                response.getResults().forEach(chunk -> {
                    Document doc = documentMap.get(chunk.getDocumentId());
                    if (doc != null) {
                        // We could add document title here if needed
                        // For now, we keep the response structure simple
                    }
                });
            }

            log.info("Semantic search completed: {} results found", response.getTotalResults());
            return response;

        } catch (Exception e) {
            log.error("Error performing semantic search: {}", e.getMessage(), e);
            throw new RuntimeException("Semantic search failed: " + e.getMessage());
        }
    }

    @PostMapping("/rag")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public RAGResponse ragQuery(@RequestBody RAGRequest request) {
        log.info("RAG query request: query='{}', topK={}", request.getQuery(), request.getTopK());

        try {
            // Call AI service for RAG query
            RAGResponse response = aiSearchClient.ragQuery(request);

            log.info("RAG query completed: {} contexts retrieved, {} characters in answer", 
                response.getTotalContexts(), response.getAnswer().length());
            return response;

        } catch (Exception e) {
            log.error("Error performing RAG query: {}", e.getMessage(), e);
            throw new RuntimeException("RAG query failed: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
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