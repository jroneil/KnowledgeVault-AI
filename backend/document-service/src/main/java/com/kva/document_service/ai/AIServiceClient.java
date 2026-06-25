package com.kva.document_service.ai;

import com.kva.document_service.ai.dto.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Client for communicating with the AI Service.
 * Handles document ingestion requests and status polling.
 */
@Service
@RequiredArgsConstructor
public class AIServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AIServiceClient.class);
    
    @Value("${ai-service.base-url:http://ai-service:8000}")
    private String aiServiceBaseUrl;
    
    @Value("${ai-service.internal-api-key}")
    private String internalApiKey;
    
    private final RestTemplate restTemplate;
    
    /**
     * Trigger document ingestion process
     * 
     * @param request Ingestion request with document and version IDs
     * @return Ingestion response with job ID
     */
    public IngestionResponse triggerIngestion(IngestionRequest request) {
        try {
            logger.info("Triggering ingestion for document: {}, version: {}", 
                request.getDocumentId(), request.getVersionId());
            
            String url = aiServiceBaseUrl + "/api/v1/processing/ingest";
            
            HttpHeaders headers = createHeaders();
            HttpEntity<IngestionRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<IngestionResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                IngestionResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.info("Ingestion started successfully. Job ID: {}", response.getBody().getJobId());
                return response.getBody();
            } else {
                logger.error("Failed to start ingestion. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to start ingestion");
            }
            
        } catch (Exception e) {
            logger.error("Error triggering ingestion", e);
            throw new RuntimeException("Failed to trigger ingestion: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get ingestion job status
     * 
     * @param jobId Job ID to check
     * @return Ingestion job status
     */
    public IngestionJobStatus getIngestionStatus(String jobId) {
        try {
            logger.debug("Checking ingestion status for job: {}", jobId);
            
            String url = aiServiceBaseUrl + "/api/v1/processing/ingest/" + jobId;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<IngestionJobStatus> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                IngestionJobStatus.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to get ingestion status");
            }
            
        } catch (Exception e) {
            logger.error("Error getting ingestion status for job: {}", jobId, e);
            throw new RuntimeException("Failed to get ingestion status: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if AI service is healthy
     * 
     * @return true if service is healthy, false otherwise
     */
    public boolean isHealthy() {
        try {
            String url = aiServiceBaseUrl + "/api/v1/health/detailed";
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (Exception e) {
            logger.warn("AI service health check failed", e);
            return false;
        }
    }
    
    /**
     * Create HTTP headers with authentication
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(internalApiKey);
        return headers;
    }
}
