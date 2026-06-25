package com.kva.document_service.versions;

import com.kva.document_service.auth.AuthenticatedUserService;
import com.kva.document_service.common.exceptions.ResourceNotFoundException;
import com.kva.document_service.documents.DocumentVersion;
import com.kva.document_service.users.User;
import com.kva.document_service.users.UserRepository;
import com.kva.document_service.versions.dto.VersionResponse;
import com.kva.document_service.versions.dto.VersionUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private final AuthenticatedUserService authenticatedUserService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
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
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<VersionUploadResponse> uploadNewVersion(
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);
        log.info("Uploading new version for document: {} by user: {}", documentId, userId);
        
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
        
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<VersionResponse> getCurrentVersion(
            @PathVariable Long documentId) {
        log.info("Getting current version for document: {}", documentId);
        
        var version = versionService.getCurrentVersion(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No current version found for document: " + documentId));
        
        return ResponseEntity.ok(mapToVersionResponse(version));
    }

    @GetMapping("/{versionNumber}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<VersionResponse> getVersionByNumber(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber) {
        log.info("Getting version {} for document: {}", versionNumber, documentId);
        
        var version = versionService.getVersionByNumber(documentId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionNumber + " not found for document: " + documentId));
        
        return ResponseEntity.ok(mapToVersionResponse(version));
    }

    @GetMapping("/{versionNumber}/download")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber) {
        log.info("Downloading version {} for document: {}", versionNumber, documentId);
        
        var loaded = versionService.loadVersion(documentId, versionNumber);
        var version = loaded.version();
        MediaType contentType = version.getMimeType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(version.getMimeType());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + version.getFileName() + "\"")
                .contentType(contentType)
                .contentLength(version.getFileSize())
                .body(loaded.resource());
    }

    @PutMapping("/{versionNumber}/set-current")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<Void> setCurrentVersion(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);
        log.info("Setting version {} as current for document: {} by user: {}",
                versionNumber, documentId, userId);
        versionService.setCurrentVersion(documentId, versionNumber, userId);
        
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{versionId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<Void> deleteVersion(
            @PathVariable Long documentId,
            @PathVariable Long versionId,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);
        log.info("Deleting version {} for document: {} by user: {}", versionId, documentId, userId);
        versionService.deleteVersion(documentId, versionId, userId);
        
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
                .uploadedBy(resolveUsername(version.getUploadedBy()))
                .uploadDate(version.getUploadDate())
                .isCurrent(version.getIsCurrent())
                .build();
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "unknown";
        }
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("unknown");
    }
}
