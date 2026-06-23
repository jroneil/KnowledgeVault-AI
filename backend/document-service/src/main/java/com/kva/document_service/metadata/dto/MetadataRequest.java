package com.kva.document_service.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetadataRequest {
    private String product;
    private String revision;
    private String department;
    private String manufacturer;
    private String category;
    private String effectiveDate;
    private String tags; // Comma-separated tags
}