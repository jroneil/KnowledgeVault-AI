package com.kva.document_service.ingestion;

import com.kva.document_service.ingestion.dto.IngestionJobResponse;
import com.kva.document_service.ingestion.dto.UpdateIngestionJobStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IngestionJobController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";

    private final IngestionJobService ingestionJobService;

    @Value("${ai-service.internal-api-key}")
    private String internalApiKey;

    @GetMapping("/ingestion-jobs/{jobId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<IngestionJobResponse> getJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(ingestionJobService.getJob(jobId));
    }

    @GetMapping("/documents/{documentId}/ingestion-jobs")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Map<String, Object>> listDocumentJobs(@PathVariable Long documentId) {
        List<IngestionJobResponse> jobs = ingestionJobService.listJobsForDocument(documentId);
        Map<String, Object> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("jobs", jobs);
        response.put("count", jobs.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/documents/{documentId}/versions/{versionNumber}/reindex")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<IngestionJobResponse> reindexDocumentVersion(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber) {
        return ResponseEntity.accepted()
                .body(ingestionJobService.requestReindex(documentId, versionNumber));
    }

    @PostMapping("/ingestion-jobs/{jobId}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<IngestionJobResponse> cancelJob(@PathVariable Long jobId) {
        return ResponseEntity.accepted()
                .body(ingestionJobService.requestCancellation(jobId));
    }

    @PutMapping("/ingestion-jobs/{jobId}/status")
    public ResponseEntity<IngestionJobResponse> updateJobStatus(
            @PathVariable Long jobId,
            @Valid @RequestBody UpdateIngestionJobStatusRequest request,
            HttpServletRequest httpRequest) {
        requireInternalApiKey(httpRequest);
        return ResponseEntity.ok(ingestionJobService.updateJobStatus(jobId, request));
    }

    private void requireInternalApiKey(HttpServletRequest request) {
        String providedKey = request.getHeader(INTERNAL_API_KEY_HEADER);
        if (providedKey == null || !providedKey.equals(internalApiKey)) {
            throw new AccessDeniedException("Invalid internal API key");
        }
    }
}
