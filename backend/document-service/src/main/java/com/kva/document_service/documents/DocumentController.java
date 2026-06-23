package com.kva.document_service.documents;

import com.kva.document_service.documents.dto.DocumentUploadResponse;
import com.kva.document_service.documents.dto.UpdateDocumentRequest;
import com.kva.document_service.documents.dto.UploadDocumentRequest;
import com.kva.document_service.versions.VersionService;
import com.kva.document_service.versions.dto.VersionResponse;
import com.kva.document_service.users.UserRepository;
import com.kva.document_service.users.User;
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
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(required = false) Long collectionId,
            @RequestParam(required = false) String status) {
        List<Document> documents;

        if (collectionId != null) {
            documents = documentService.listDocumentsByCollection(collectionId);
        } else if (status != null) {
            documents = documentService.listDocumentsByStatus(status);
        } else {
            documents = documentService.listDocuments();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("documents", documents);
        response.put("count", documents.size());
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
            @RequestPart("metadata") UploadDocumentRequest metadata,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);

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
        Long userId = getUserIdFromAuthentication(authentication);
        Document updated = documentService.updateDocument(id, request, userId);

        log.info("Document updated: {} by user: {}", id, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
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

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<List<VersionResponse>> getDocumentVersions(@PathVariable Long id) {
        var versions = versionService.getVersionHistory(id);

        List<VersionResponse> response = versions.stream()
                .map(this::mapToVersionResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<Map<String, Object>> uploadNewVersion(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);

        var newVersion = versionService.uploadNewVersion(id, file, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("versionId", newVersion.getId());
        response.put("documentId", newVersion.getDocumentId());
        response.put("versionNumber", newVersion.getVersionNumber());
        response.put("fileName", newVersion.getFileName());
        response.put("message", "New version uploaded successfully");

        log.info("New version uploaded: {} for document: {} by user: {}",
                newVersion.getVersionNumber(), id, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    private Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            return 1L;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElse(1L);
    }

    private VersionResponse mapToVersionResponse(DocumentVersion version) {
        String uploader = "User";
        if (version.getUploadedBy() != null) {
            uploader = userRepository.findById(version.getUploadedBy())
                    .map(User::getUsername)
                    .orElse("User");
        }
        return VersionResponse.builder()
                .id(version.getId())
                .documentId(version.getDocumentId())
                .versionNumber(version.getVersionNumber())
                .fileName(version.getFileName())
                .fileSize(version.getFileSize())
                .mimeType(version.getMimeType())
                .uploadDate(version.getUploadDate())
                .isCurrent(version.getIsCurrent())
                .uploadedBy(uploader)
                .build();
    }
}