package com.kva.document_service.ingestion;

public class DocumentTextExtractionException extends RuntimeException {

    private final String errorCode;

    public DocumentTextExtractionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DocumentTextExtractionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
