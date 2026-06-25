package com.kva.document_service.common;

import com.kva.document_service.common.exceptions.BusinessException;
import com.kva.document_service.common.exceptions.DuplicateResourceException;
import com.kva.document_service.common.exceptions.ResourceNotFoundException;
import com.kva.document_service.storage.StorageException;
import com.kva.document_service.storage.StorageFileNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                exception.getMessage(), request, null);
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    ResponseEntity<ApiError> handleFileNotFound(
            StorageFileNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND",
                exception.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    ResponseEntity<ApiError> handleDuplicate(
            DuplicateResourceException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                exception.getMessage(), request, null);
    }

    @ExceptionHandler({BusinessException.class, StorageException.class})
    ResponseEntity<ApiError> handleBusiness(
            RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION",
                exception.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action", request, null);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestPartException.class
    })
    ResponseEntity<ApiError> handleValidation(
            Exception exception, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        if (exception instanceof MethodArgumentNotValidException invalid) {
            invalid.getBindingResult().getFieldErrors().forEach(error ->
                    validationErrors.put(error.getField(), error.getDefaultMessage()));
        }
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", request, validationErrors);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        log.error("Unexpected request failure. correlationId={}", correlationId, exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request, null, correlationId);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors) {
        return response(status, code, message, request, validationErrors,
                correlationId(request));
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors,
            String correlationId) {
        return ResponseEntity.status(status)
                .header("X-Correlation-ID", correlationId)
                .body(ApiError.builder()
                        .code(code)
                        .message(message)
                        .status(status.value())
                        .path(request.getRequestURI())
                        .correlationId(correlationId)
                        .timestamp(Instant.now())
                        .validationErrors(validationErrors)
                        .build());
    }

    private String correlationId(HttpServletRequest request) {
        String existing = request.getHeader("X-Correlation-ID");
        return existing == null || existing.isBlank()
                ? UUID.randomUUID().toString()
                : existing;
    }
}
