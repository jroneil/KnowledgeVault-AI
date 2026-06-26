package com.kva.document_service.search;

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

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class FastApiLlmCompletionClient implements LlmCompletionClient {

    private final RestTemplate restTemplate;

    @Value("${ai-service.base-url}")
    private String aiServiceBaseUrl;

    @Value("${ai-service.internal-api-key}")
    private String internalApiKey;

    @Value("${ai-service.internal-api-key-header:Authorization}")
    private String internalApiKeyHeader;

    public FastApiLlmCompletionClient(@Value("${ai-service.connect-timeout-ms:3000}") int connectTimeoutMs,
                                      @Value("${ai-service.read-timeout-ms:30000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
        String url = aiServiceBaseUrl + "/api/v1/chat/completions";
        HttpEntity<ChatCompletionRequestPayload> entity = new HttpEntity<>(
                ChatCompletionRequestPayload.builder()
                        .messages(List.of(ChatCompletionMessage.builder()
                                .role("user")
                                .content(request.getPrompt())
                                .build()))
                        .model(request.getModelName())
                        .temperature(request.getTemperature())
                        .maxTokens(request.getMaxTokens())
                        .build(),
                createHeaders()
        );

        try {
            ResponseEntity<ChatCompletionResponsePayload> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ChatCompletionResponsePayload.class
            );
            ChatCompletionResponsePayload body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || !Boolean.TRUE.equals(body.getSuccess())) {
                return LlmCompletionResult.builder()
                        .success(false)
                        .retryable(false)
                        .errorCode("LLM_COMPLETION_FAILED")
                        .errorMessage("LLM completion request did not return a valid response")
                        .modelName(request.getModelName())
                        .build();
            }

            return LlmCompletionResult.builder()
                    .success(true)
                    .retryable(false)
                    .answer(body.getResponse())
                    .modelName(body.getModel() == null || body.getModel().isBlank()
                            ? request.getModelName()
                            : body.getModel())
                    .build();
        } catch (ResourceAccessException exception) {
            return LlmCompletionResult.builder()
                    .success(false)
                    .retryable(isTimeoutException(exception))
                    .errorCode(isTimeoutException(exception) ? "LLM_TIMEOUT" : "LLM_SERVICE_UNAVAILABLE")
                    .errorMessage(exception.getMessage())
                    .modelName(request.getModelName())
                    .build();
        } catch (RestClientException exception) {
            log.warn("LLM completion request failed", exception);
            return LlmCompletionResult.builder()
                    .success(false)
                    .retryable(false)
                    .errorCode("LLM_COMPLETION_FAILED")
                    .errorMessage(exception.getMessage())
                    .modelName(request.getModelName())
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
