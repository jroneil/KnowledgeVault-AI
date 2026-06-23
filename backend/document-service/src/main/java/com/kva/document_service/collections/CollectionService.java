package com.kva.document_service.collections;

import com.kva.document_service.collections.dto.CollectionStatistics;
import com.kva.document_service.collections.dto.CreateCollectionRequest;
import com.kva.document_service.collections.dto.UpdateCollectionRequest;
import com.kva.document_service.common.exceptions.BusinessException;
import com.kva.document_service.common.exceptions.DuplicateResourceException;
import com.kva.document_service.common.exceptions.ResourceNotFoundException;
import com.kva.document_service.documents.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final DocumentRepository documentRepository;

    @Transactional
    public Collection createCollection(CreateCollectionRequest request, Long userId) {
        if (collectionRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Collection with name '" + request.getName() + "' already exists");
        }

        Collection collection = Collection.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isActive(true)
                .createdBy(userId)
                .build();

        Collection saved = collectionRepository.save(collection);
        log.info("Created collection: {} by user: {}", saved.getName(), userId);
        return saved;
    }

    @Transactional
    public Collection updateCollection(Long id, UpdateCollectionRequest request, Long userId) {
        Collection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found with id: " + id));

        // Check if name is being changed and if it conflicts with existing collection
        if (!collection.getName().equals(request.getName()) &&
            collectionRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Collection with name '" + request.getName() + "' already exists");
        }

        collection.setName(request.getName());
        collection.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            collection.setIsActive(request.getIsActive());
        }

        Collection updated = collectionRepository.update(collection);
        log.info("Updated collection: {} by user: {}", updated.getId(), userId);
        return updated;
    }

    @Transactional
    public void deleteCollection(Long id, Long userId) {
        // Check if collection exists
        if (!collectionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Collection not found with id: " + id);
        }

        // Check if collection has documents
        long documentCount = documentRepository.countByCollectionId(id);
        if (documentCount > 0) {
            throw new BusinessException("Cannot delete collection with " + documentCount + " documents. " +
                    "Please move or delete documents first.");
        }

        collectionRepository.deleteById(id);
        log.info("Deleted collection: {} by user: {}", id, userId);
    }

    public Collection getCollection(Long id) {
        return collectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found with id: " + id));
    }

    public List<Collection> listCollections(boolean activeOnly) {
        if (activeOnly) {
            return collectionRepository.findActive();
        }
        return collectionRepository.findAll();
    }

    public List<Collection> getCollectionsByUser(Long userId) {
        return collectionRepository.findByCreatedBy(userId);
    }

    public long getTotalCollections() {
        return collectionRepository.count();
    }

    public long getActiveCollections() {
        return collectionRepository.countActive();
    }

    public CollectionStatistics getCollectionStatistics(Long collectionId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

        long documentCount = documentRepository.countByCollectionId(collectionId);

        return CollectionStatistics.builder()
                .collectionId(collection.getId())
                .collectionName(collection.getName())
                .documentCount(documentCount)
                .isActive(collection.getIsActive())
                .createdAt(collection.getCreatedAt())
                .build();
    }
}