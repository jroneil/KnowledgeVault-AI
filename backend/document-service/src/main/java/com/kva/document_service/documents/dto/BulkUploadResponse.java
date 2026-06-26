package com.kva.document_service.documents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadResponse {
    private int processedCount;
    private int succeededCount;
    private int failedCount;
    private int needsReviewCount;
    private List<BulkUploadItemResponse> results;
}
