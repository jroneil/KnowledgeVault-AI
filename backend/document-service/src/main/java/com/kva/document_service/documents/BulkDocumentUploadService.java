package com.kva.document_service.documents;

import com.kva.document_service.documents.dto.BulkUploadDocumentRequest;
import com.kva.document_service.documents.dto.BulkUploadItemResponse;
import com.kva.document_service.documents.dto.BulkUploadResponse;
import com.kva.document_service.documents.dto.DocumentUploadResponse;
import com.kva.document_service.metadata.BulkMetadataExtractionService;
import com.kva.document_service.metadata.DocumentMetadata;
import com.kva.document_service.metadata.DocumentMetadataExtractionResult;
import com.kva.document_service.metadata.DocumentMetadataExtractionResultRepository;
import com.kva.document_service.metadata.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkDocumentUploadService {

    private final DocumentService documentService;
    private final DocumentMetadataRepository metadataRepository;
    private final DocumentMetadataExtractionResultRepository extractionResultRepository;
    private final BulkMetadataExtractionService bulkMetadataExtractionService;

    public BulkUploadResponse uploadDocuments(BulkUploadDocumentRequest request,
                                              MultipartFile[] files,
                                              Long userId) {
        List<BulkUploadItemResponse> results = new ArrayList<>();

        if (files == null || files.length == 0) {
            return BulkUploadResponse.builder()
                    .processedCount(0)
                    .succeededCount(0)
                    .failedCount(0)
                    .needsReviewCount(0)
                    .results(List.of())
                    .build();
        }

        for (MultipartFile file : files) {
            results.add(processSingleFile(request, file, userId));
        }

        int succeededCount = (int) results.stream().filter(BulkUploadItemResponse::isSuccess).count();
        int failedCount = results.size() - succeededCount;
        int needsReviewCount = (int) results.stream()
                .filter(BulkUploadItemResponse::isSuccess)
                .filter(BulkUploadItemResponse::isNeedsReview)
                .count();

        return BulkUploadResponse.builder()
                .processedCount(results.size())
                .succeededCount(succeededCount)
                .failedCount(failedCount)
                .needsReviewCount(needsReviewCount)
                .results(results)
                .build();
    }

    private BulkUploadItemResponse processSingleFile(BulkUploadDocumentRequest request,
                                                     MultipartFile file,
                                                     Long userId) {
        String fileName = file.getOriginalFilename();
        try {
            DocumentMetadataExtractionResult extractionResult = bulkMetadataExtractionService.extract(file);
            String resolvedTitle = resolveTitle(request, extractionResult, fileName);
            DocumentMetadata mergedMetadata = mergeMetadata(request, extractionResult);

            DocumentUploadResponse uploadResponse = documentService.uploadDocumentWithResolvedMetadata(
                    request.getCollectionId(),
                    resolvedTitle,
                    request.getDescription(),
                    mergedMetadata,
                    file,
                    userId
            );

            DocumentMetadata savedMetadata = metadataRepository.findByDocumentId(uploadResponse.getDocumentId()).orElse(null);
            DocumentMetadataExtractionResult persistedExtraction = extractionResultRepository.upsert(
                    DocumentMetadataExtractionResult.builder()
                            .documentId(uploadResponse.getDocumentId())
                            .extractedTitle(extractionResult.getExtractedTitle())
                            .extractedManufacturer(extractionResult.getExtractedManufacturer())
                            .extractedModel(extractionResult.getExtractedModel())
                            .extractedDocumentType(extractionResult.getExtractedDocumentType())
                            .extractedDocumentNumber(extractionResult.getExtractedDocumentNumber())
                            .extractedRevision(extractionResult.getExtractedRevision())
                            .extractedLanguage(extractionResult.getExtractedLanguage())
                            .extractedPublicationDate(extractionResult.getExtractedPublicationDate())
                            .extractedPageCount(extractionResult.getExtractedPageCount())
                            .extractedTags(extractionResult.getExtractedTags())
                            .confidenceByField(extractionResult.getConfidenceByField())
                            .sourceSummary(extractionResult.getSourceSummary())
                            .needsReview(extractionResult.isNeedsReview())
                            .build()
            );

            return BulkUploadItemResponse.builder()
                    .fileName(fileName)
                    .success(true)
                    .documentId(uploadResponse.getDocumentId())
                    .versionId(uploadResponse.getVersionId())
                    .title(uploadResponse.getTitle())
                    .message(uploadResponse.getMessage())
                    .needsReview(persistedExtraction.isNeedsReview())
                    .metadata(savedMetadata)
                    .extractionResult(persistedExtraction)
                    .build();
        } catch (Exception ex) {
            log.warn("Bulk upload failed for file {}", fileName, ex);
            return BulkUploadItemResponse.builder()
                    .fileName(fileName)
                    .success(false)
                    .error(ex.getMessage())
                    .message("Upload failed")
                    .needsReview(false)
                    .build();
        }
    }

    private String resolveTitle(BulkUploadDocumentRequest request,
                                DocumentMetadataExtractionResult extractionResult,
                                String fileName) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle().trim();
        }
        if (extractionResult.getExtractedTitle() != null && !extractionResult.getExtractedTitle().isBlank()) {
            return extractionResult.getExtractedTitle();
        }
        if (fileName == null || fileName.isBlank()) {
            return "Untitled Document";
        }
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0 ? fileName.substring(0, lastDot) : fileName).trim();
    }

    private DocumentMetadata mergeMetadata(BulkUploadDocumentRequest request,
                                           DocumentMetadataExtractionResult extractionResult) {
        List<String> manualTags = splitTags(request.getTags());
        List<String> extractedTags = extractionResult.getExtractedTags();

        return DocumentMetadata.builder()
                .product(firstNonBlank(request.getProduct(), null))
                .revision(firstNonBlank(request.getRevision(), extractionResult.getExtractedRevision()))
                .department(firstNonBlank(request.getDepartment(), null))
                .manufacturer(firstNonBlank(request.getManufacturer(), extractionResult.getExtractedManufacturer()))
                .model(firstNonBlank(request.getModel(), extractionResult.getExtractedModel()))
                .documentType(firstNonBlank(request.getDocumentType(), extractionResult.getExtractedDocumentType()))
                .documentNumber(firstNonBlank(request.getDocumentNumber(), extractionResult.getExtractedDocumentNumber()))
                .language(firstNonBlank(request.getLanguage(), extractionResult.getExtractedLanguage()))
                .category(firstNonBlank(request.getCategory(), extractionResult.getExtractedDocumentType()))
                .effectiveDate(parseDate(request.getEffectiveDate()))
                .publicationDate(firstNonNull(parseDate(request.getPublicationDate()), extractionResult.getExtractedPublicationDate()))
                .pageCount(firstNonNull(request.getPageCount(), extractionResult.getExtractedPageCount()))
                .tags(!manualTags.isEmpty() ? manualTags : extractedTags)
                .build();
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }
}
