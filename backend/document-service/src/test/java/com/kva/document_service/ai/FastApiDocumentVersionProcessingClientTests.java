package com.kva.document_service.ai;

import com.kva.document_service.ai.dto.DocumentVersionProcessingRequest;
import com.kva.document_service.ai.dto.DocumentVersionProcessingResponse;
import com.kva.document_service.ai.dto.IngestionResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FastApiDocumentVersionProcessingClientTests {

    @Test
    void callsFastApiIngestionEndpointWithExpectedPayloadAndAuthorization() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FastApiDocumentVersionProcessingClient client = new FastApiDocumentVersionProcessingClient(restTemplate);
        ReflectionTestUtils.setField(client, "aiServiceBaseUrl", "http://ai-service:8000");
        ReflectionTestUtils.setField(client, "internalApiKey", "secret-key");
        ReflectionTestUtils.setField(client, "internalApiKeyHeader", "Authorization");

        DocumentVersionProcessingRequest request = DocumentVersionProcessingRequest.builder()
                .jobId(55L)
                .documentId(42L)
                .versionId(100L)
                .storagePath("/storage/originals/7/42/v1.txt")
                .originalFilename("manual.txt")
                .contentType("text/plain")
                .build();
        IngestionResponse response = new IngestionResponse();
        response.setJobId("remote-1");
        response.setDocumentId(42L);
        response.setVersionId(100L);
        response.setStatus("COMPLETED");
        response.setMessage("accepted");

        when(restTemplate.exchange(
                eq("http://ai-service:8000/api/v1/processing/ingest"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(IngestionResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        DocumentVersionProcessingResponse result = client.processDocumentVersion(request);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).exchange(
                eq("http://ai-service:8000/api/v1/processing/ingest"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(IngestionResponse.class)
        );

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) entityCaptor.getValue().getBody();

        assertEquals("Bearer secret-key", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(55L, payload.get("job_id"));
        assertEquals(42L, payload.get("document_id"));
        assertEquals(100L, payload.get("version_id"));
        assertEquals("/storage/originals/7/42/v1.txt", payload.get("file_path"));
        assertTrue(result.isSuccess());
        assertNull(result.getErrorCode());
    }

    @Test
    void returnsFailureWhenFastApiReportsFailedStatus() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FastApiDocumentVersionProcessingClient client = new FastApiDocumentVersionProcessingClient(restTemplate);
        ReflectionTestUtils.setField(client, "aiServiceBaseUrl", "http://ai-service:8000");
        ReflectionTestUtils.setField(client, "internalApiKey", "secret-key");
        ReflectionTestUtils.setField(client, "internalApiKeyHeader", "Authorization");

        DocumentVersionProcessingRequest request = DocumentVersionProcessingRequest.builder()
                .jobId(55L)
                .documentId(42L)
                .versionId(100L)
                .storagePath("/storage/originals/7/42/v1.txt")
                .contentType("text/plain")
                .build();
        IngestionResponse response = new IngestionResponse();
        response.setStatus("FAILED");
        response.setMessage("ingestion failed");

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(IngestionResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        DocumentVersionProcessingResponse result = client.processDocumentVersion(request);

        assertEquals(false, result.isSuccess());
        assertEquals("AI_SERVICE_FAILED", result.getErrorCode());
        assertEquals("ingestion failed", result.getErrorMessage());
        assertEquals(false, result.getRetryable());
    }

    @Test
    void returnsFailureWhenFastApiResponseIsInvalid() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FastApiDocumentVersionProcessingClient client = new FastApiDocumentVersionProcessingClient(restTemplate);
        ReflectionTestUtils.setField(client, "aiServiceBaseUrl", "http://ai-service:8000");
        ReflectionTestUtils.setField(client, "internalApiKey", "secret-key");
        ReflectionTestUtils.setField(client, "internalApiKeyHeader", "Authorization");

        DocumentVersionProcessingRequest request = DocumentVersionProcessingRequest.builder()
                .jobId(55L)
                .documentId(42L)
                .versionId(100L)
                .storagePath("/storage/originals/7/42/v1.txt")
                .contentType("text/plain")
                .build();

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(IngestionResponse.class)))
                .thenReturn(new ResponseEntity<IngestionResponse>((IngestionResponse) null, HttpStatus.OK));

        DocumentVersionProcessingResponse result = client.processDocumentVersion(request);

        assertEquals(false, result.isSuccess());
        assertEquals("AI_SERVICE_UNAVAILABLE", result.getErrorCode());
        assertEquals(false, result.getRetryable());
    }

    @Test
    void returnsRetryableFailureWhenFastApiTimesOut() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FastApiDocumentVersionProcessingClient client = new FastApiDocumentVersionProcessingClient(restTemplate);
        ReflectionTestUtils.setField(client, "aiServiceBaseUrl", "http://ai-service:8000");
        ReflectionTestUtils.setField(client, "internalApiKey", "secret-key");
        ReflectionTestUtils.setField(client, "internalApiKeyHeader", "Authorization");

        DocumentVersionProcessingRequest request = DocumentVersionProcessingRequest.builder()
                .jobId(55L)
                .documentId(42L)
                .versionId(100L)
                .storagePath("/storage/originals/7/42/v1.txt")
                .contentType("text/plain")
                .build();

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(IngestionResponse.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        DocumentVersionProcessingResponse result = client.processDocumentVersion(request);

        assertEquals(false, result.isSuccess());
        assertEquals("AI_TIMEOUT", result.getErrorCode());
        assertEquals(true, result.getRetryable());
    }
}
