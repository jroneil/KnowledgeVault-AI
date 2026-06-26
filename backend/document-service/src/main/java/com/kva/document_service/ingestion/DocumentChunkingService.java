package com.kva.document_service.ingestion;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkingService {

    private final IngestionChunkingProperties properties;

    public DocumentChunkingService(IngestionChunkingProperties properties) {
        this.properties = properties;
    }

    public List<DocumentChunkDraft> createChunks(DocumentExtraction extraction) {
        if (!properties.isEnabled() || extraction == null || extraction.getPages() == null || extraction.getPages().isEmpty()) {
            return List.of();
        }

        StringBuilder combined = new StringBuilder();
        List<PageSpan> pageSpans = new ArrayList<>();

        for (DocumentExtractionPage page : extraction.getPages()) {
            String text = normalize(page.getExtractedText());
            if (text == null) {
                continue;
            }

            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }

            int start = combined.length();
            combined.append(text);
            int end = combined.length();

            pageSpans.add(new PageSpan(page.getPageNumber(), start, end));
        }

        String allText = combined.toString();
        if (allText.isBlank()) {
            return List.of();
        }

        int chunkSize = Math.max(1, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getOverlap(), chunkSize - 1));

        List<DocumentChunkDraft> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;

        while (start < allText.length()) {
            int end = Math.min(allText.length(), start + chunkSize);
            String content = allText.substring(start, end).trim();

            if (!content.isBlank()) {
                int sourcePageFrom = resolveSourcePage(pageSpans, start, true);
                int sourcePageTo = resolveSourcePage(pageSpans, Math.max(start, end - 1), false);

                chunks.add(DocumentChunkDraft.builder()
                        .chunkIndex(chunkIndex++)
                        .content(content)
                        .sourcePageFrom(sourcePageFrom)
                        .sourcePageTo(sourcePageTo)
                        .tokenCount(approximateTokenCount(content))
                        .build());
            }

            if (end >= allText.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }

        return chunks;
    }

    boolean spansMultiplePages(DocumentChunkDraft chunk) {
        return chunk.getSourcePageFrom() != null
                && chunk.getSourcePageTo() != null
                && !chunk.getSourcePageFrom().equals(chunk.getSourcePageTo());
    }

    private int resolveSourcePage(List<PageSpan> pageSpans, int index, boolean useFirst) {
        for (PageSpan span : pageSpans) {
            if (index >= span.start && index < span.end) {
                return span.pageNumber;
            }
        }
        return useFirst
                ? pageSpans.get(0).pageNumber
                : pageSpans.get(pageSpans.size() - 1).pageNumber;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int approximateTokenCount(String content) {
        String normalized = normalize(content);
        return normalized == null ? 0 : normalized.split("\\s+").length;
    }

    private record PageSpan(int pageNumber, int start, int end) {
    }
}
