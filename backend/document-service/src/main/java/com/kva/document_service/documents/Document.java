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
public class Document {
    private Long id;
    private Long collectionId;
    private String title;
    private String description;
    private String status;
    private Integer currentVersion;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}