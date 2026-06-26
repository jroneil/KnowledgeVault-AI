package com.kva.document_service.ingestion;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocumentPageOcrResult {
    boolean success;
    String text;
    Double confidence;
    String errorMessage;
}
