package com.kva.document_service.metadata;

import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.metadata.dto.MetadataRequest;
import com.kva.document_service.metadata.dto.MetadataSearchRequest;
import com.kva.document_service.users.UserRepository;
import com.kva.document_service.users.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class MetadataController {

    private final MetadataService metadataService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @GetMapping("/documents/{id}/metadata")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<?> getDocumentMetadata(@PathVariable Long id) {
        // Verify document exists
        if (!documentRepository.existsById(id)) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Document not found with id: " + id);
            return ResponseEntity.status(404).body(error);
        }

        if (metadataService.getMetadata(id).isPresent()) {
            return ResponseEntity.ok().body(metadataService.getMetadata(id).get());
        } else {
            return ResponseEntity.ok().body(Map.of("message", "No metadata found for document"));
        }
    }

    @PutMapping("/documents/{id}/metadata")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<DocumentMetadata> updateDocumentMetadata(
            @PathVariable Long id,
            @Valid @RequestBody MetadataRequest request,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);

        DocumentMetadata updated = metadataService.updateMetadata(id, request, userId);

        log.info("Updated metadata for document: {} by user: {}", id, userId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/metadata/search")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Map<String, Object>> searchDocumentsByMetadata(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String revision,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        MetadataSearchRequest request = MetadataSearchRequest.builder()
                .searchTerm(searchTerm)
                .product(product)
                .revision(revision)
                .department(department)
                .manufacturer(manufacturer)
                .category(category)
                .tag(tag)
                .build();

        List<DocumentMetadata> results = metadataService.searchDocumentsPaged(request, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("count", results.size());
        response.put("page", page);
        response.put("size", size);
        response.put("criteria", Map.of(
                "searchTerm", searchTerm,
                "product", product,
                "revision", revision,
                "department", department,
                "manufacturer", manufacturer,
                "category", category,
                "tag", tag
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search/by-product/{product}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<List<DocumentMetadata>> getByProduct(@PathVariable String product) {
        List<DocumentMetadata> results = metadataService.getByProduct(product);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/by-department/{department}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<List<DocumentMetadata>> getByDepartment(@PathVariable String department) {
        List<DocumentMetadata> results = metadataService.getByDepartment(department);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/by-category/{category}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<List<DocumentMetadata>> getByCategory(@PathVariable String category) {
        List<DocumentMetadata> results = metadataService.getByCategory(category);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/by-tag/{tag}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<List<DocumentMetadata>> getByTag(@PathVariable String tag) {
        List<DocumentMetadata> results = metadataService.getByTag(tag);
        return ResponseEntity.ok(results);
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            return 1L;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElse(1L);
    }
}