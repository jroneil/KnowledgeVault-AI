package com.kva.document_service.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadataExtractionResult {
    private Long id;
    private Long documentId;
    private String extractedTitle;
    private String extractedManufacturer;
    private String extractedModel;
    private String extractedDocumentType;
    private String extractedDocumentNumber;
    private String extractedRevision;
    private String extractedLanguage;
    private LocalDate extractedPublicationDate;
    private Integer extractedPageCount;
    private List<String> extractedTags;
    private Map<String, Double> confidenceByField;
    private String sourceSummary;
    private boolean needsReview;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
