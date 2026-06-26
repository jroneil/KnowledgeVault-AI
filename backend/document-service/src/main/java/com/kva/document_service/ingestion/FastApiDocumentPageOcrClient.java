package com.kva.document_service.ingestion;

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

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FastApiDocumentPageOcrClient implements DocumentPageOcrClient {

    private final RestTemplate restTemplate;
    private final IngestionOcrProperties ingestionOcrProperties;

    @Value("${ai-service.base-url}")
    private String aiServiceBaseUrl;

    @Value("${ai-service.internal-api-key}")
    private String internalApiKey;

    @Value("${ai-service.internal-api-key-header:Authorization}")
    private String internalApiKeyHeader;

    @Override
    public DocumentPageOcrResult performOcr(Path imagePath) {
        String url = aiServiceBaseUrl + "/api/v1/ocr/process";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildPayload(imagePath), createHeaders());

        try {
            ResponseEntity<OcrProcessResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    OcrProcessResponse.class
            );

            OcrProcessResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                return DocumentPageOcrResult.builder()
                        .success(false)
                        .text("")
                        .confidence(0.0d)
                        .errorMessage("OCR request did not return a valid response")
                        .build();
            }

            return DocumentPageOcrResult.builder()
                    .success(true)
                    .text(body.getText() == null ? "" : body.getText().trim())
                    .confidence(body.getConfidence())
                    .errorMessage(null)
                    .build();
        } catch (ResourceAccessException exception) {
            log.warn("OCR request timed out or could not reach AI service for {}", imagePath, exception);
            return DocumentPageOcrResult.builder()
                    .success(false)
                    .text("")
                    .confidence(0.0d)
                    .errorMessage(exception.getMessage())
                    .build();
        } catch (RestClientException exception) {
            log.warn("OCR request failed for {}", imagePath, exception);
            return DocumentPageOcrResult.builder()
                    .success(false)
                    .text("")
                    .confidence(0.0d)
                    .errorMessage(exception.getMessage())
                    .build();
        }
    }

    private Map<String, Object> buildPayload(Path imagePath) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("file_path", imagePath.toAbsolutePath().toString());
        payload.put("output_format", "text");
        payload.put("languages", List.copyOf(ingestionOcrProperties.getLanguages()));
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
}
