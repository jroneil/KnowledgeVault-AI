package com.kva.document_service.search;

import com.kva.document_service.search.dto.RAGCitation;
import com.kva.document_service.search.dto.RAGContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RagCitationService {

    public List<RAGCitation> formatCitations(List<RAGContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        List<RAGCitation> citations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (int i = 0; i < contexts.size(); i++) {
            RAGContext context = contexts.get(i);
            String dedupeKey = dedupeKey(context);
            if (!seen.add(dedupeKey)) {
                continue;
            }

            citations.add(RAGCitation.builder()
                    .label(buildLabel(context, i + 1))
                    .documentId(context.getDocumentId())
                    .documentTitle(context.getDocumentTitle())
                    .versionId(context.getVersionId())
                    .versionNumber(context.getVersionNumber())
                    .revision(context.getRevision())
                    .pageNumber(context.getPageNumber())
                    .sourcePageFrom(context.getSourcePageFrom())
                    .sourcePageTo(context.getSourcePageTo())
                    .chunkId(context.getChunkId())
                    .chunkIndex(context.getChunkIndex())
                    .rank(i + 1)
                    .similarityScore(context.getSimilarityScore())
                    .sourceLabel(context.getSourceLabel())
                    .build());
        }

        return citations;
    }

    private String buildLabel(RAGContext context, int rank) {
        List<String> parts = new ArrayList<>();
        parts.add("[" + rank + "]");
        parts.add(hasText(context.getDocumentTitle())
                ? context.getDocumentTitle()
                : "Document " + context.getDocumentId());

        if (context.getVersionNumber() != null) {
            parts.add("v" + context.getVersionNumber());
        }
        if (hasText(context.getRevision())) {
            parts.add("rev " + context.getRevision());
        }
        if (context.getPageNumber() != null) {
            parts.add("p. " + context.getPageNumber());
        } else if (context.getSourcePageFrom() != null || context.getSourcePageTo() != null) {
            parts.add("pp. " + pageRange(context.getSourcePageFrom(), context.getSourcePageTo()));
        }
        if (context.getChunkId() != null) {
            parts.add("chunk " + context.getChunkId());
        } else if (context.getChunkIndex() != null) {
            parts.add("chunk #" + context.getChunkIndex());
        }
        if (context.getSimilarityScore() != null) {
            parts.add("score " + String.format("%.3f", context.getSimilarityScore()));
        }

        return String.join(", ", parts);
    }

    private String dedupeKey(RAGContext context) {
        if (context.getChunkId() != null) {
            return "chunk:" + context.getChunkId();
        }
        return String.join("|",
                String.valueOf(context.getDocumentId()),
                String.valueOf(context.getVersionId()),
                String.valueOf(context.getPageNumber()),
                String.valueOf(context.getSourcePageFrom()),
                String.valueOf(context.getSourcePageTo()),
                String.valueOf(context.getChunkIndex()));
    }

    private String pageRange(Integer from, Integer to) {
        if (from == null) {
            return String.valueOf(to);
        }
        if (to == null || from.equals(to)) {
            return String.valueOf(from);
        }
        return from + "-" + to;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
