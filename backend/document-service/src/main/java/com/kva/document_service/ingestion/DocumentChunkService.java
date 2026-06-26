package com.kva.document_service.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentChunkService {

    private final DocumentChunkRepository documentChunkRepository;

    @Transactional(readOnly = true)
    public List<DocumentChunk> getChunksForVersion(Long versionId) {
        return documentChunkRepository.findByVersionIdOrderByChunkIndexAsc(versionId);
    }

    @Transactional
    public List<DocumentChunk> replaceChunks(Long ingestionJobId,
                                             Long documentId,
                                             Long versionId,
                                             List<DocumentChunkDraft> chunkDrafts) {
        documentChunkRepository.deleteByVersionId(versionId);

        List<DocumentChunk> chunks = chunkDrafts.stream()
                .map(chunk -> DocumentChunk.builder()
                        .documentId(documentId)
                        .versionId(versionId)
                        .ingestionJobId(ingestionJobId)
                        .chunkIndex(chunk.getChunkIndex())
                        .content(chunk.getContent())
                        .pageNumber(chunk.getSourcePageFrom())
                        .sourcePageFrom(chunk.getSourcePageFrom())
                        .sourcePageTo(chunk.getSourcePageTo())
                        .sectionName(null)
                        .tokenCount(chunk.getTokenCount())
                        .build())
                .toList();

        return documentChunkRepository.saveAll(chunks);
    }
}
