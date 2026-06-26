package com.kva.document_service.ingestion;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocumentProcessingValidationResult {
    boolean valid;
    String errorCode;
    String errorMessage;
}
