package com.kva.document_service.documents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersion {
    private Long id;
    private Long documentId;
    private Integer versionNumber;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private Long uploadedBy;
    private LocalDateTime uploadDate;
    private Boolean isCurrent;
}