package com.kva.document_service.collections;

import com.kva.document_service.auth.AuthenticatedUserService;
import com.kva.document_service.collections.dto.CollectionStatistics;
import com.kva.document_service.collections.dto.CreateCollectionRequest;
import com.kva.document_service.collections.dto.UpdateCollectionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
@Slf4j
public class CollectionController {

    private final CollectionService collectionService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Map<String, Object>> getAllCollections(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        validatePageRequest(page, size);
        List<Collection> collections = collectionService.listCollections(activeOnly);
        int fromIndex = (int) Math.min((long) page * size, collections.size());
        int toIndex = Math.min(fromIndex + size, collections.size());
        List<Collection> pageContent = collections.subList(fromIndex, toIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("collections", pageContent);
        response.put("count", pageContent.size());
        response.put("totalElements", collections.size());
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (collections.size() + size - 1) / size);
        response.put("activeOnly", activeOnly);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<Collection> createCollection(
            @Valid @RequestBody CreateCollectionRequest request,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);
        Collection created = collectionService.createCollection(request, userId);

        log.info("Created collection: {} by user: {}", created.getName(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Collection> getCollection(@PathVariable Long id) {
        Collection collection = collectionService.getCollection(id);
        return ResponseEntity.ok(collection);
    }

    @GetMapping("/{id}/statistics")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<CollectionStatistics> getCollectionStatistics(@PathVariable Long id) {
        CollectionStatistics stats = collectionService.getCollectionStatistics(id);
        return ResponseEntity.ok(stats);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR')")
    public ResponseEntity<Collection> updateCollection(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCollectionRequest request,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);
        Collection updated = collectionService.updateCollection(id, request, userId);

        log.info("Updated collection: {} by user: {}", id, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteCollection(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = authenticatedUserService.requireUserId(authentication);
        collectionService.deleteCollection(id, userId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Collection deleted successfully");
        response.put("collectionId", id.toString());

        log.info("Deleted collection: {} by user: {}", id, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/overview")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public ResponseEntity<Map<String, Long>> getOverviewStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalCollections", collectionService.getTotalCollections());
        stats.put("activeCollections", collectionService.getActiveCollections());

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
