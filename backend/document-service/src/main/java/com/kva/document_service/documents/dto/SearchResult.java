package com.kva.document_service.documents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private Long documentId;
    private Long collectionId;
    private String collectionName;
    private String title;
    private String description;
    private String status;
    private Integer currentVersion;
    
    // Metadata fields
    private String product;
    private String revision;
    private String department;
    private String manufacturer;
    private String category;
    private List<String> tags;
    private LocalDate effectiveDate;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}