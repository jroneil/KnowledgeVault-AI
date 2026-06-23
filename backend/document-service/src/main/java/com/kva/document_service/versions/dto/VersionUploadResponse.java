package com.kva.document_service.versions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionUploadResponse {
    private Long versionId;
    private Long documentId;
    private Integer versionNumber;
    private String fileName;
    private Long fileSize;
    private LocalDateTime uploadDate;
    private String message;
}