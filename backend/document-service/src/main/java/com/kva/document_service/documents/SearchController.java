package com.kva.document_service.documents;

import com.kva.document_service.collections.Collection;
import com.kva.document_service.collections.CollectionRepository;
import com.kva.document_service.documents.dto.SearchResult;
import java.util.Optional;
import com.kva.document_service.metadata.DocumentMetadata;
import com.kva.document_service.metadata.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final DocumentRepository documentRepository;
    private final DocumentMetadataRepository metadataRepository;
    private final CollectionRepository collectionRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public List<SearchResult> searchDocuments(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long collection,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String revision,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String status) {
        
        log.info("Searching documents with filters - title: {}, collection: {}, product: {}, revision: {}, department: {}, manufacturer: {}, category: {}, tags: {}, status: {}",
                title, collection, product, revision, department, manufacturer, category, tags, status);

        List<SearchResult> results = new ArrayList<>();

        // If title search is provided, do title-based search
        if (title != null && !title.isEmpty()) {
            List<Document> titleResults = documentRepository.searchByTitle(title);
            for (Document doc : titleResults) {
                if (matchesFilters(doc, collection, status)) {
                    results.add(buildSearchResult(doc));
                }
            }
        } else {
            // Otherwise, search by metadata filters
            List<DocumentMetadata> metadataResults = metadataRepository.findByFilters(
                    product, revision, department, manufacturer, category, tags);
            
            for (DocumentMetadata metadata : metadataResults) {
                Document doc = documentRepository.findById(metadata.getDocumentId()).orElse(null);
                if (doc != null && matchesFilters(doc, collection, status)) {
                    SearchResult result = buildSearchResult(doc);
                    result.setProduct(metadata.getProduct());
                    result.setRevision(metadata.getRevision());
                    result.setDepartment(metadata.getDepartment());
                    result.setManufacturer(metadata.getManufacturer());
                    result.setCategory(metadata.getCategory());
                    result.setTags(metadata.getTags());
                    result.setEffectiveDate(metadata.getEffectiveDate());
                    results.add(result);
                }
            }
        }

        log.info("Found {} documents matching search criteria", results.size());
        return results;
    }

    @GetMapping("/collections/{collectionId}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRIBUTOR', 'VIEWER')")
    public List<SearchResult> searchInCollection(
            @PathVariable Long collectionId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String revision,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> tags) {
        
        log.info("Searching documents in collection: {} with filters", collectionId);
        
        // Verify collection exists
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new RuntimeException("Collection not found with id: " + collectionId));

        if (!collection.getIsActive()) {
            return List.of(); // Return empty list for inactive collections
        }

        List<SearchResult> results = new ArrayList<>();

        // Search by title if provided
        if (title != null && !title.isEmpty()) {
            List<Document> titleResults = documentRepository.searchByTitle(title);
            for (Document doc : titleResults) {
                if (doc.getCollectionId().equals(collectionId)) {
                    results.add(buildSearchResult(doc));
                }
            }
        } else {
            // Search by metadata filters within the collection
            List<Document> collectionDocs = documentRepository.findByCollectionId(collectionId);
            
            for (Document doc : collectionDocs) {
                DocumentMetadata metadata = metadataRepository.findByDocumentId(doc.getId()).orElse(null);
                if (metadata != null && matchesMetadataFilters(metadata, product, revision, department, manufacturer, category, tags)) {
                    SearchResult result = buildSearchResult(doc);
                    result.setProduct(metadata.getProduct());
                    result.setRevision(metadata.getRevision());
                    result.setDepartment(metadata.getDepartment());
                    result.setManufacturer(metadata.getManufacturer());
                    result.setCategory(metadata.getCategory());
                    result.setTags(metadata.getTags());
                    result.setEffectiveDate(metadata.getEffectiveDate());
                    results.add(result);
                }
            }
        }

        log.info("Found {} documents in collection: {}", results.size(), collectionId);
        return results;
    }

    private boolean matchesFilters(Document doc, Long collectionId, String status) {
        if (collectionId != null && !doc.getCollectionId().equals(collectionId)) {
            return false;
        }
        if (status != null && !status.isEmpty() && !status.equals(doc.getStatus())) {
            return false;
        }
        return true;
    }

    private boolean matchesMetadataFilters(DocumentMetadata metadata, String product, String revision, 
                                           String department, String manufacturer, String category, List<String> tags) {
        if (product != null && !product.isEmpty() && !product.equals(metadata.getProduct())) {
            return false;
        }
        if (revision != null && !revision.isEmpty() && !revision.equals(metadata.getRevision())) {
            return false;
        }
        if (department != null && !department.isEmpty() && !department.equals(metadata.getDepartment())) {
            return false;
        }
        if (manufacturer != null && !manufacturer.isEmpty() && !manufacturer.equals(metadata.getManufacturer())) {
            return false;
        }
        if (category != null && !category.isEmpty() && !category.equals(metadata.getCategory())) {
            return false;
        }
        if (tags != null && !tags.isEmpty() && metadata.getTags() != null) {
            // Check if any of the requested tags match
            boolean hasTag = tags.stream().anyMatch(tag -> metadata.getTags().contains(tag));
            if (!hasTag) {
                return false;
            }
        }
        return true;
    }

    private SearchResult buildSearchResult(Document doc) {
        Collection collection = collectionRepository.findById(doc.getCollectionId()).orElse(null);
        
        return SearchResult.builder()
                .documentId(doc.getId())
                .collectionId(doc.getCollectionId())
                .collectionName(collection != null ? collection.getName() : "Unknown Collection")
                .title(doc.getTitle())
                .description(doc.getDescription())
                .status(doc.getStatus())
                .currentVersion(doc.getCurrentVersion())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .createdBy(doc.getCreatedBy())
                .build();
    }
}