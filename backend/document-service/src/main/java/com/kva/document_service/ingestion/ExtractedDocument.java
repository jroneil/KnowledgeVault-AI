package com.kva.document_service.ingestion;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExtractedDocument {
    String extractorType;
    Integer pageCount;
    Integer characterCount;
    List<ExtractedDocumentPage> pages;
}
