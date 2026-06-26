package com.kva.document_service.ingestion;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExtractedDocumentPage {
    Integer pageNumber;
    String extractedText;
    String originalText;
    String ocrText;
    Boolean ocrApplied;
    Integer characterCount;
}
