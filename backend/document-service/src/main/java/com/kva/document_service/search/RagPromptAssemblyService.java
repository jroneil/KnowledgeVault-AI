package com.kva.document_service.search;

import com.kva.document_service.documents.Document;
import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.metadata.DocumentMetadata;
import com.kva.document_service.metadata.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RagPromptAssemblyService {

    private final DocumentRepository documentRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final RagPromptAssemblyProperties ragPromptAssemblyProperties;

    public RagPromptPackage assemble(String question, List<SemanticRetrievalResult> retrievedChunks) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question is required");
        }

        List<SemanticRetrievalResult> orderedResults = retrievedChunks == null ? List.of() : retrievedChunks;
        List<RagPromptContextItem> contexts = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        int totalEstimatedTokens = 0;
        boolean truncated = false;
        int rank = 1;

        for (SemanticRetrievalResult result : orderedResults) {
            String dedupeKey = dedupeKey(result);
            if (!seenKeys.add(dedupeKey)) {
                continue;
            }

            Optional<Document> document = documentRepository.findById(result.getDocumentId());
            Optional<DocumentMetadata> metadata = documentMetadataRepository.findByDocumentId(result.getDocumentId());
            int estimatedTokens = estimateTokens(result);

            if (totalEstimatedTokens + estimatedTokens > ragPromptAssemblyProperties.getMaxContextTokens()) {
                truncated = true;
                continue;
            }

            RagPromptContextItem contextItem = RagPromptContextItem.builder()
                    .rank(rank++)
                    .chunkId(result.getChunkId())
                    .documentId(result.getDocumentId())
                    .documentTitle(document.map(Document::getTitle).orElse(null))
                    .versionId(result.getVersionId())
                    .versionNumber(result.getVersionNumber())
                    .chunkIndex(result.getChunkIndex())
                    .revision(metadata.map(DocumentMetadata::getRevision).orElse(null))
                    .product(metadata.map(DocumentMetadata::getProduct).orElse(null))
                    .category(metadata.map(DocumentMetadata::getCategory).orElse(null))
                    .tags(metadata.map(DocumentMetadata::getTags).orElse(null))
                    .pageNumber(result.getPageNumber())
                    .sourcePageFrom(result.getSourcePageFrom())
                    .sourcePageTo(result.getSourcePageTo())
                    .similarityScore(result.getSimilarityScore())
                    .estimatedTokens(estimatedTokens)
                    .content(result.getContent())
                    .sourceLabel(buildSourceLabel(document.orElse(null), metadata.orElse(null), result))
                    .build();

            contexts.add(contextItem);
            totalEstimatedTokens += estimatedTokens;
        }

        return RagPromptPackage.builder()
                .question(question)
                .prompt(buildPrompt(question, contexts))
                .contexts(contexts)
                .totalContexts(contexts.size())
                .totalEstimatedTokens(totalEstimatedTokens)
                .truncated(truncated)
                .build();
    }

    private String buildPrompt(String question, List<RagPromptContextItem> contexts) {
        StringBuilder builder = new StringBuilder()
                .append("Question:\n")
                .append(question)
                .append("\n\n")
                .append("Retrieved Context:\n");

        if (contexts.isEmpty()) {
            builder.append("(no relevant context retrieved)");
            return builder.toString();
        }

        for (RagPromptContextItem context : contexts) {
            builder.append('[').append(context.getRank()).append("] ")
                    .append(context.getSourceLabel()).append('\n')
                    .append(context.getContent()).append("\n\n");
        }

        return builder.toString().trim();
    }

    private String buildSourceLabel(Document document, DocumentMetadata metadata, SemanticRetrievalResult result) {
        List<String> parts = new ArrayList<>();

        if (document != null && hasText(document.getTitle())) {
            parts.add("Document: " + document.getTitle());
        } else {
            parts.add("Document ID: " + result.getDocumentId());
        }

        if (result.getVersionNumber() != null) {
            parts.add("Version: " + result.getVersionNumber());
        }
        if (metadata != null && hasText(metadata.getRevision())) {
            parts.add("Revision: " + metadata.getRevision());
        }
        if (result.getPageNumber() != null) {
            parts.add("Page: " + result.getPageNumber());
        } else if (result.getSourcePageFrom() != null || result.getSourcePageTo() != null) {
            parts.add("Pages: " + pageRange(result.getSourcePageFrom(), result.getSourcePageTo()));
        }
        parts.add("Chunk: " + result.getChunkIndex());
        parts.add("Document ID: " + result.getDocumentId());
        if (result.getVersionId() != null) {
            parts.add("Version ID: " + result.getVersionId());
        }
        if (metadata != null && hasText(metadata.getProduct())) {
            parts.add("Product: " + metadata.getProduct());
        }
        if (metadata != null && hasText(metadata.getCategory())) {
            parts.add("Category: " + metadata.getCategory());
        }

        return String.join(" | ", parts);
    }

    private int estimateTokens(SemanticRetrievalResult result) {
        if (result.getTokenCount() != null && result.getTokenCount() > 0) {
            return result.getTokenCount();
        }
        if (result.getContent() == null || result.getContent().isBlank()) {
            return 0;
        }
        return result.getContent().trim().split("\\s+").length;
    }

    private String dedupeKey(SemanticRetrievalResult result) {
        if (result.getChunkId() != null) {
            return "chunk:" + result.getChunkId();
        }
        return String.join("|",
                String.valueOf(result.getDocumentId()),
                String.valueOf(result.getVersionId()),
                String.valueOf(result.getSourcePageFrom()),
                String.valueOf(result.getSourcePageTo()),
                normalize(result.getContent()));
    }

    private String pageRange(Integer from, Integer to) {
        if (Objects.equals(from, to)) {
            return String.valueOf(from);
        }
        if (from == null) {
            return String.valueOf(to);
        }
        if (to == null) {
            return String.valueOf(from);
        }
        return from + "-" + to;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
