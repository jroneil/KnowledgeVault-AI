package com.kva.document_service.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentExtractionService {

    private final DocumentExtractionRepository documentExtractionRepository;

    @Transactional(readOnly = true)
    public DocumentExtraction getExtractionForJob(Long ingestionJobId) {
        return documentExtractionRepository.findByIngestionJobId(ingestionJobId).orElse(null);
    }

    @Transactional(readOnly = true)
    public DocumentExtraction getExtractionForVersion(Long versionId) {
        return documentExtractionRepository.findByVersionId(versionId).orElse(null);
    }

    @Transactional
    public DocumentExtraction replaceExtraction(Long ingestionJobId,
                                                Long documentId,
                                                Long versionId,
                                                ExtractedDocument extractedDocument) {
        documentExtractionRepository.deleteByVersionId(versionId);

        DocumentExtraction extraction = DocumentExtraction.builder()
                .ingestionJobId(ingestionJobId)
                .documentId(documentId)
                .versionId(versionId)
                .extractorType(extractedDocument.getExtractorType())
                .pageCount(extractedDocument.getPageCount())
                .characterCount(extractedDocument.getCharacterCount())
                .build();

        extractedDocument.getPages().forEach(page -> extraction.addPage(
                DocumentExtractionPage.builder()
                        .pageNumber(page.getPageNumber())
                        .extractedText(page.getExtractedText())
                        .originalText(page.getOriginalText())
                        .ocrText(page.getOcrText())
                        .ocrApplied(Boolean.TRUE.equals(page.getOcrApplied()))
                        .characterCount(page.getCharacterCount())
                        .build()
        ));

        return documentExtractionRepository.save(extraction);
    }
}
