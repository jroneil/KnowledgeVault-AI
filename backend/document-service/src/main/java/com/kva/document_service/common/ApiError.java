package com.kva.document_service.common;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class ApiError {
    private String code;
    private String message;
    private int status;
    private String path;
    private String correlationId;
    private Instant timestamp;
    private Map<String, String> validationErrors;
}
