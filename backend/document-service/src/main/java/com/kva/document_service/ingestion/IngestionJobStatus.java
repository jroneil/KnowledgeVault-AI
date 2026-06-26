package com.kva.document_service.ingestion;

public enum IngestionJobStatus {
    PENDING,
    PROCESSING,
    INDEXED,
    FAILED,
    CANCELLED
}
