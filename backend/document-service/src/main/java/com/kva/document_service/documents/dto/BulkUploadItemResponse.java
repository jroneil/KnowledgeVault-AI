package com.kva.document_service.documents.dto;

import com.kva.document_service.metadata.DocumentMetadata;
import com.kva.document_service.metadata.DocumentMetadataExtractionResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadItemResponse {
    private String fileName;
    private boolean success;
    private Long documentId;
    private Long versionId;
    private String title;
    private String message;
    private String error;
    private boolean needsReview;
    private DocumentMetadata metadata;
    private DocumentMetadataExtractionResult extractionResult;
}
