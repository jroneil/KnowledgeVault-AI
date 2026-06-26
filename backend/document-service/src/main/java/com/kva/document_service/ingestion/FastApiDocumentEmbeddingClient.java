package com.kva.document_service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class FastApiDocumentEmbeddingClient implements DocumentEmbeddingClient {

    private final RestTemplate restTemplate;
    private final IngestionEmbeddingProperties ingestionEmbeddingProperties;

    @Value("${ai-service.base-url}")
    private String aiServiceBaseUrl;

    @Value("${ai-service.internal-api-key}")
    private String internalApiKey;

    @Value("${ai-service.internal-api-key-header:Authorization}")
    private String internalApiKeyHeader;

    public FastApiDocumentEmbeddingClient(IngestionEmbeddingProperties ingestionEmbeddingProperties) {
        this.ingestionEmbeddingProperties = ingestionEmbeddingProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofMillis(ingestionEmbeddingProperties.getTimeoutMs()));
        requestFactory.setReadTimeout(java.time.Duration.ofMillis(ingestionEmbeddingProperties.getTimeoutMs()));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public EmbeddingBatchResult generateEmbeddings(GenerateEmbeddingsBatchRequest request) {
        String url = aiServiceBaseUrl + "/api/v1/embeddings/batch";
        HttpEntity<GenerateEmbeddingsBatchRequest> entity = new HttpEntity<>(request, createHeaders());

        try {
            ResponseEntity<GenerateEmbeddingsBatchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    GenerateEmbeddingsBatchResponse.class
            );
            GenerateEmbeddingsBatchResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || !Boolean.TRUE.equals(body.getSuccess())) {
                return EmbeddingBatchResult.builder()
                        .success(false)
                        .retryable(false)
                        .errorCode("EMBEDDING_SERVICE_FAILED")
                        .errorMessage("Embedding batch request did not return a valid response")
                        .provider(ingestionEmbeddingProperties.getProvider())
                        .modelName(request.getModel())
                        .modelVersion(ingestionEmbeddingProperties.getModelVersion())
                        .build();
            }

            return EmbeddingBatchResult.builder()
                    .success(true)
                    .retryable(false)
                    .embeddings(body.getEmbeddings())
                    .provider(ingestionEmbeddingProperties.getProvider())
                    .modelName(body.getModel())
                    .modelVersion(ingestionEmbeddingProperties.getModelVersion())
                    .build();
        } catch (ResourceAccessException exception) {
            return EmbeddingBatchResult.builder()
                    .success(false)
                    .retryable(isTimeoutException(exception))
                    .errorCode(isTimeoutException(exception) ? "EMBEDDING_TIMEOUT" : "EMBEDDING_SERVICE_UNAVAILABLE")
                    .errorMessage(exception.getMessage())
                    .provider(ingestionEmbeddingProperties.getProvider())
                    .modelName(request.getModel())
                    .modelVersion(ingestionEmbeddingProperties.getModelVersion())
                    .build();
        } catch (RestClientException exception) {
            log.warn("Embedding batch request failed", exception);
            return EmbeddingBatchResult.builder()
                    .success(false)
                    .retryable(false)
                    .errorCode("EMBEDDING_SERVICE_FAILED")
                    .errorMessage(exception.getMessage())
                    .provider(ingestionEmbeddingProperties.getProvider())
                    .modelName(request.getModel())
                    .modelVersion(ingestionEmbeddingProperties.getModelVersion())
                    .build();
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if ("Authorization".equalsIgnoreCase(internalApiKeyHeader)) {
            headers.setBearerAuth(internalApiKey);
        } else {
            headers.set(internalApiKeyHeader, internalApiKey);
        }
        return headers;
    }

    private boolean isTimeoutException(ResourceAccessException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }
}
