package com.kva.document_service.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionProcessingRequest {
    private Long jobId;
    private Long documentId;
    private Long versionId;
    private String storagePath;
    private String originalFilename;
    private String contentType;
}
