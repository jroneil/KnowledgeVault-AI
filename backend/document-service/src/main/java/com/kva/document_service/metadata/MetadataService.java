package com.kva.document_service.metadata;

import com.kva.document_service.common.exceptions.ResourceNotFoundException;
import com.kva.document_service.documents.Document;
import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.metadata.dto.MetadataRequest;
import com.kva.document_service.metadata.dto.MetadataSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

    private final DocumentMetadataRepository metadataRepository;
    private final DocumentRepository documentRepository;

    @Transactional
    public DocumentMetadata updateMetadata(Long documentId, MetadataRequest request, Long userId) {
        // Verify document exists
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        DocumentMetadata metadata = metadataRepository.findByDocumentId(documentId)
                .orElse(DocumentMetadata.builder()
                        .documentId(documentId)
                        .build());

        // Update fields from request
        if (request.getProduct() != null) {
            metadata.setProduct(request.getProduct());
        }
        if (request.getRevision() != null) {
            metadata.setRevision(request.getRevision());
        }
        if (request.getDepartment() != null) {
            metadata.setDepartment(request.getDepartment());
        }
        if (request.getManufacturer() != null) {
            metadata.setManufacturer(request.getManufacturer());
        }
        if (request.getModel() != null) {
            metadata.setModel(request.getModel());
        }
        if (request.getDocumentType() != null) {
            metadata.setDocumentType(request.getDocumentType());
        }
        if (request.getDocumentNumber() != null) {
            metadata.setDocumentNumber(request.getDocumentNumber());
        }
        if (request.getLanguage() != null) {
            metadata.setLanguage(request.getLanguage());
        }
        if (request.getCategory() != null) {
            metadata.setCategory(request.getCategory());
        }
        if (request.getEffectiveDate() != null) {
            try {
                metadata.setEffectiveDate(LocalDate.parse(request.getEffectiveDate()));
            } catch (Exception e) {
                log.warn("Invalid effective date format: {}", request.getEffectiveDate());
            }
        }
        if (request.getPublicationDate() != null) {
            try {
                metadata.setPublicationDate(LocalDate.parse(request.getPublicationDate()));
            } catch (Exception e) {
                log.warn("Invalid publication date format: {}", request.getPublicationDate());
            }
        }
        if (request.getPageCount() != null) {
            metadata.setPageCount(request.getPageCount());
        }
        if (request.getTags() != null) {
            List<String> tagsList = Arrays.asList(request.getTags().split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            metadata.setTags(tagsList);
        }

        DocumentMetadata saved = metadataRepository.existsByDocumentId(documentId)
                ? metadataRepository.update(metadata)
                : metadataRepository.save(metadata);

        log.info("Updated metadata for document: {} by user: {}", documentId, userId);
        return saved;
    }

    public Optional<DocumentMetadata> getMetadata(Long documentId) {
        return metadataRepository.findByDocumentId(documentId);
    }

    public List<DocumentMetadata> searchDocuments(MetadataSearchRequest request) {
        // If searching by title/description, use document repository
        if (request.getSearchTerm() != null && !request.getSearchTerm().isEmpty()) {
            List<Long> documentIds = documentRepository.searchByTitle(request.getSearchTerm())
                    .stream()
                    .map(Document::getId)
                    .toList();

            if (documentIds.isEmpty()) {
                return List.of();
            }

            // Filter by metadata fields if provided
            return filterByMetadataFields(documentIds, request);
        }

        // Search by metadata fields only
        return searchByMetadataFields(request);
    }

    public List<DocumentMetadata> searchDocumentsPaged(MetadataSearchRequest request, int page, int size) {
        List<DocumentMetadata> results = searchDocuments(request);

        int start = page * size;
        int end = Math.min(start + size, results.size());

        if (start >= results.size()) {
            return List.of();
        }

        return results.subList(start, end);
    }

    public List<DocumentMetadata> getByProduct(String product) {
        return metadataRepository.findByProduct(product);
    }

    public List<DocumentMetadata> getByDepartment(String department) {
        return metadataRepository.findByDepartment(department);
    }

    public List<DocumentMetadata> getByCategory(String category) {
        return metadataRepository.findByCategory(category);
    }

    public List<DocumentMetadata> getByTag(String tag) {
        return metadataRepository.findByTag(tag);
    }

    private List<DocumentMetadata> filterByMetadataFields(List<Long> documentIds, MetadataSearchRequest request) {
        List<DocumentMetadata> metadataList = documentIds.stream()
                .map(metadataRepository::findByDocumentId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        return filterMetadataList(metadataList, request);
    }

    private List<DocumentMetadata> searchByMetadataFields(MetadataSearchRequest request) {
        if (request.getProduct() != null) {
            return filterAndSearchByProduct(request);
        }
        if (request.getDepartment() != null) {
            return filterByDepartment(request);
        }
        if (request.getCategory() != null) {
            return filterByCategory(request);
        }
        if (request.getTag() != null) {
            return metadataRepository.findByTag(request.getTag());
        }
        if (request.getSearchTerm() != null && !request.getSearchTerm().isEmpty()) {
            return metadataRepository.searchByMetadata(request.getSearchTerm());
        }

        return metadataRepository.findAll();
    }

    private List<DocumentMetadata> filterAndSearchByProduct(MetadataSearchRequest request) {
        List<DocumentMetadata> results = metadataRepository.findByProduct(request.getProduct());
        return filterMetadataList(results, request);
    }

    private List<DocumentMetadata> filterByDepartment(MetadataSearchRequest request) {
        List<DocumentMetadata> results = metadataRepository.findByDepartment(request.getDepartment());
        return filterMetadataList(results, request);
    }

    private List<DocumentMetadata> filterByCategory(MetadataSearchRequest request) {
        List<DocumentMetadata> results = metadataRepository.findByCategory(request.getCategory());
        return filterMetadataList(results, request);
    }

    private List<DocumentMetadata> filterMetadataList(List<DocumentMetadata> metadataList, MetadataSearchRequest request) {
        return metadataList.stream()
                .filter(metadata -> {
                    if (request.getRevision() != null && !request.getRevision().isEmpty()) {
                        return request.getRevision().equals(metadata.getRevision());
                    }
                    return true;
                })
                .filter(metadata -> {
                    if (request.getManufacturer() != null && !request.getManufacturer().isEmpty()) {
                        return request.getManufacturer().equals(metadata.getManufacturer());
                    }
                    return true;
                })
                .filter(metadata -> {
                    if (request.getCategory() != null && !request.getCategory().isEmpty()) {
                        return request.getCategory().equals(metadata.getCategory());
                    }
                    return true;
                })
                .filter(metadata -> {
                    if (request.getDepartment() != null && !request.getDepartment().isEmpty()) {
                        return request.getDepartment().equals(metadata.getDepartment());
                    }
                    return true;
                })
                .filter(metadata -> {
                    if (request.getTag() != null && !request.getTag().isEmpty()) {
                        return metadata.getTags() != null && metadata.getTags().contains(request.getTag());
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }
}
