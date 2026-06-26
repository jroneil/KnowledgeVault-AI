package com.kva.document_service.ai;

import com.kva.document_service.ai.dto.DocumentVersionProcessingRequest;
import com.kva.document_service.ai.dto.DocumentVersionProcessingResponse;
import com.kva.document_service.ai.dto.IngestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FastApiDocumentVersionProcessingClient implements DocumentVersionProcessingClient {

    private final RestTemplate restTemplate;

    @Value("${ai-service.base-url}")
    private String aiServiceBaseUrl;

    @Value("${ai-service.internal-api-key}")
    private String internalApiKey;

    @Value("${ai-service.internal-api-key-header:Authorization}")
    private String internalApiKeyHeader;

    @Override
    public DocumentVersionProcessingResponse processDocumentVersion(DocumentVersionProcessingRequest request) {
        String url = aiServiceBaseUrl + "/api/v1/processing/ingest";
        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildPayload(request), headers);

        log.info("Handing off ingestion job {} for document {} version {} to FastAPI",
                request.getJobId(), request.getDocumentId(), request.getVersionId());

        ResponseEntity<IngestionResponse> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    IngestionResponse.class
            );
        } catch (ResourceAccessException exception) {
            if (isTimeoutException(exception)) {
                return DocumentVersionProcessingResponse.builder()
                        .success(false)
                        .chunkCount(0)
                        .embeddingCount(0)
                        .warningMessages(List.of())
                        .errorCode("AI_TIMEOUT")
                        .errorMessage(exception.getMessage())
                        .retryable(true)
                        .build();
            }
            return DocumentVersionProcessingResponse.builder()
                    .success(false)
                    .chunkCount(0)
                    .embeddingCount(0)
                    .warningMessages(List.of())
                    .errorCode("AI_SERVICE_UNAVAILABLE")
                    .errorMessage(exception.getMessage())
                    .retryable(false)
                    .build();
        } catch (RestClientException exception) {
            return DocumentVersionProcessingResponse.builder()
                    .success(false)
                    .chunkCount(0)
                    .embeddingCount(0)
                    .warningMessages(List.of())
                    .errorCode("AI_SERVICE_FAILED")
                    .errorMessage(exception.getMessage())
                    .retryable(false)
                    .build();
        }

        IngestionResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            return DocumentVersionProcessingResponse.builder()
                    .success(false)
                    .chunkCount(0)
                    .embeddingCount(0)
                    .warningMessages(List.of())
                    .errorCode("AI_SERVICE_UNAVAILABLE")
                    .errorMessage("FastAPI ingestion request did not return a valid response")
                    .retryable(false)
                    .build();
        }

        boolean success = body.getStatus() != null && !"FAILED".equalsIgnoreCase(body.getStatus());
        return DocumentVersionProcessingResponse.builder()
                .success(success)
                .chunkCount(0)
                .embeddingCount(0)
                .warningMessages(body.getMessage() == null || body.getMessage().isBlank()
                        ? List.of()
                        : List.of(body.getMessage()))
                .errorCode(success ? null : "AI_SERVICE_FAILED")
                .errorMessage(success ? null : defaultFailureMessage(body))
                .retryable(false)
                .build();
    }

    private Map<String, Object> buildPayload(DocumentVersionProcessingRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_id", request.getJobId());
        payload.put("document_id", request.getDocumentId());
        payload.put("version_id", request.getVersionId());
        payload.put("file_path", request.getStoragePath());
        payload.put("original_filename", request.getOriginalFilename());
        payload.put("mime_type", request.getContentType());
        return payload;
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

    private String defaultFailureMessage(IngestionResponse response) {
        return response.getMessage() == null || response.getMessage().isBlank()
                ? "FastAPI ingestion request failed"
                : response.getMessage();
    }

    private boolean isTimeoutException(ResourceAccessException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }
}
