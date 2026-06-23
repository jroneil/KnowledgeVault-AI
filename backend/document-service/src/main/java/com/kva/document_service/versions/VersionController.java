package com.kva.document_service.versions;

import com.kva.document_service.documents.DocumentVersion;
import com.kva.document_service.versions.dto.VersionResponse;
import com.kva.document_service.versions.dto.VersionUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/documents/{documentId}/versions")
@RequiredArgsConstructor
@Slf4j
public class VersionController {

    private final VersionService versionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<List<VersionResponse>> getVersionHistory(
            @PathVariable Long documentId) {
        log.info("Getting version history for document: {}", documentId);
        List<VersionResponse> versions = versionService.getVersionHistory(documentId)
                .stream()
                .map(this::mapToVersionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(versions);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<VersionUploadResponse> uploadNewVersion(
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Uploading new version for document: {} by user: {}", documentId, userDetails.getUsername());
        
        // Get user ID from authenticated user (this would need proper implementation)
        Long userId = getUserIdFromUserDetails(userDetails);
        
        var version = versionService.uploadNewVersion(documentId, file, userId);
        
        VersionUploadResponse response = VersionUploadResponse.builder()
                .versionId(version.getId())
                .documentId(version.getDocumentId())
                .versionNumber(version.getVersionNumber())
                .fileName(version.getFileName())
                .fileSize(version.getFileSize())
                .uploadDate(version.getUploadDate())
                .message("New version uploaded successfully")
                .build();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<VersionResponse> getCurrentVersion(
            @PathVariable Long documentId) {
        log.info("Getting current version for document: {}", documentId);
        
        var version = versionService.getCurrentVersion(documentId)
                .orElseThrow(() -> new RuntimeException("No current version found for document: " + documentId));
        
        return ResponseEntity.ok(mapToVersionResponse(version));
    }

    @GetMapping("/{versionNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<VersionResponse> getVersionByNumber(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber) {
        log.info("Getting version {} for document: {}", versionNumber, documentId);
        
        var version = versionService.getVersionByNumber(documentId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version " + versionNumber + " not found for document: " + documentId));
        
        return ResponseEntity.ok(mapToVersionResponse(version));
    }

    @GetMapping("/{versionNumber}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber) {
        log.info("Downloading version {} for document: {}", versionNumber, documentId);
        
        var version = versionService.getVersionByNumber(documentId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version " + versionNumber + " not found for document: " + documentId));
        
        // This would need FileStorageService to be injected and used
        // For now, returning a placeholder response
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + version.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(version.getMimeType()))
                .build();
    }

    @PutMapping("/{versionNumber}/set-current")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<Void> setCurrentVersion(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Setting version {} as current for document: {} by user: {}", versionNumber, documentId, userDetails.getUsername());
        
        Long userId = getUserIdFromUserDetails(userDetails);
        versionService.setCurrentVersion(documentId, versionNumber, userId);
        
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{versionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<Void> deleteVersion(
            @PathVariable Long documentId,
            @PathVariable Long versionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Deleting version {} for document: {} by user: {}", versionId, documentId, userDetails.getUsername());
        
        Long userId = getUserIdFromUserDetails(userDetails);
        versionService.deleteVersion(versionId, userId);
        
        return ResponseEntity.noContent().build();
    }

    private VersionResponse mapToVersionResponse(DocumentVersion version) {
        return VersionResponse.builder()
                .id(version.getId())
                .documentId(version.getDocumentId())
                .versionNumber(version.getVersionNumber())
                .fileName(version.getFileName())
                .fileSize(version.getFileSize())
                .mimeType(version.getMimeType())
                .uploadedBy(version.getUploadedBy() != null ? version.getUploadedBy().toString() : "unknown")
                .uploadDate(version.getUploadDate())
                .isCurrent(version.getIsCurrent())
                .build();
    }

    private Long getUserIdFromUserDetails(UserDetails userDetails) {
        // This is a placeholder - in a real implementation, you would:
        // 1. Have a UserService that can look up user by username
        // 2. Return the actual user ID
        // For now, return a default value
        return 1L; // Default to admin user
    }
}