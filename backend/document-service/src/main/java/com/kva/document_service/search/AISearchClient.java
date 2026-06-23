package com.kva.document_service.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kva.document_service.search.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Client for interacting with AI Service search endpoints.
 * Handles semantic search and RAG queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AISearchClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.service.base-url:http://ai-service:8000}")
    private String aiServiceBaseUrl;

    @Value("${ai.service.internal-api-key:your-internal-api-key-change-in-production}")
    private String internalApiKey;

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(internalApiKey);
        return headers;
    }

    /**
     * Perform semantic search using vector similarity.
     */
    public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
        String url = aiServiceBaseUrl + "/api/v1/search/semantic";
        
        try {
            HttpEntity<SemanticSearchRequest> entity = new HttpEntity<>(request, createHeaders());
            ResponseEntity<SemanticSearchResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                SemanticSearchResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Semantic search completed successfully. Found {} results", 
                    response.getBody().getTotalResults());
                return response.getBody();
            } else {
                throw new RuntimeException("Semantic search failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error performing semantic search: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform semantic search: " + e.getMessage(), e);
        }
    }

    /**
     * Perform RAG (Retrieval-Augmented Generation) query.
     */
    public RAGResponse ragQuery(RAGRequest request) {
        String url = aiServiceBaseUrl + "/api/v1/search/rag";
        
        try {
            HttpEntity<RAGRequest> entity = new HttpEntity<>(request, createHeaders());
            ResponseEntity<RAGResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                RAGResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("RAG query completed successfully. Retrieved {} contexts", 
                    response.getBody().getTotalContexts());
                return response.getBody();
            } else {
                throw new RuntimeException("RAG query failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error performing RAG query: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform RAG query: " + e.getMessage(), e);
        }
    }

    /**
     * Get search statistics.
     */
    public SearchStats getSearchStats() {
        String url = aiServiceBaseUrl + "/api/v1/search/stats";
        
        try {
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<SearchStats> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                SearchStats.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to get search stats: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error getting search stats: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get search stats: " + e.getMessage(), e);
        }
    }
}