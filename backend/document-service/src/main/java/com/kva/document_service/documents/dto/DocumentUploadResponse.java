package com.kva.document_service.documents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {
    private Long documentId;
    private Long collectionId;
    private String title;
    private Integer currentVersion;
    private Long versionId;
    private String fileName;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private String message;
}