package com.kva.document_service.ingestion;

import lombok.Data;

@Data
public class OcrProcessResponse {
    private String text;
    private Double confidence;
    private Integer pagesProcessed;
    private Double processingTimeMs;
    private String languageDetected;
}
