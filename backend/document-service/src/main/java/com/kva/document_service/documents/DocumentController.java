package com.kva.document_service.documents;

import com.kva.document_service.auth.AuthenticatedUserService;
import com.kva.document_service.documents.dto.DocumentUploadResponse;
import com.kva.document_service.documents.dto.UpdateDocumentRequest;
import com.kva.document_service.documents.dto.UploadDocumentRequest;
import com.kva.document_service.versions.VersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;
    private final VersionService versionService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(required = false) Long collectionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        validatePageRequest(page, size);
        List<Document> documents;

        if (collectionId != null) {
            documents = documentService.listDocumentsByCollection(collectionId);
        } else if (status != null) {
            documents = documentService.listDocumentsByStatus(status);
        } else {
            documents = documentService.listDocuments();
        }

        int fromIndex = (int) Math.min((long) page * size, documents.size());
        int toIndex = Math.min(fromIndex + size, documents.size());
        List<Document> pageContent = documents.subList(fromIndex, toIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("documents", pageContent);
        response.put("count", pageContent.size());
        response.put("totalElements", documents.size());
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (documents.size() + size - 1) / size);
        response.put("collectionId", collectionId);
        response.put("status", status);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Document> getDocument(@PathVariable Long id) {
        Document document = documentService.getDocument(id);
        return ResponseEntity.ok(document);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @Valid @RequestPart("metadata") UploadDocumentRequest metadata,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);

        DocumentUploadResponse response = documentService.uploadDocument(metadata, file, userId);

        log.info("Document uploaded: {} by user: {}", response.getDocumentId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<Document> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);
        Document updated = documentService.updateDocument(id, request, userId);

        log.info("Document updated: {} by user: {}", id, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);
        documentService.deleteDocument(id, userId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Document deleted successfully");
        response.put("documentId", id.toString());

        log.info("Document deleted: {} by user: {}", id, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        Document document = documentService.getDocument(id);

        if (!"ACTIVE".equals(document.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        byte[] fileContent = documentService.downloadDocument(id);

        // Get current version info for filename
        var currentVersion = versionService.getCurrentVersion(id);

        String filename = currentVersion
                .map(version -> version.getFileName())
                .orElse("document");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileContent.length)
                .body(new org.springframework.core.io.ByteArrayResource(fileContent));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Map<String, Object>> searchDocuments(@RequestParam String searchTerm) {
        List<Document> results = documentService.searchDocuments(searchTerm);

        Map<String, Object> response = new HashMap<>();
        response.put("documents", results);
        response.put("count", results.size());
        response.put("searchTerm", searchTerm);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/overview")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Map<String, Long>> getOverviewStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalDocuments", documentService.getTotalDocuments());

        return ResponseEntity.ok(stats);
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.kva.document_service.common.exceptions.BusinessException(
                    "Page must be at least 0 and size must be between 1 and 100"
            );
        }
    }
}
