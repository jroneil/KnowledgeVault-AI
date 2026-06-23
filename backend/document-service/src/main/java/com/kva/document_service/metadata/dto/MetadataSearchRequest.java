package com.kva.document_service.metadata.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetadataSearchRequest {
    private String searchTerm; // Search across all fields
    private String product;
    private String revision;
    private String department;
    private String manufacturer;
    private String category;
    private String tag;
    private String tags; // Comma-separated tags for filtering
}