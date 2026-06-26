package com.kva.document_service.search;

import com.kva.document_service.ingestion.IngestionEmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SemanticRetrievalService {

    private final SemanticRetrievalRepository semanticRetrievalRepository;
    private final SemanticRetrievalProperties semanticRetrievalProperties;
    private final IngestionEmbeddingProperties ingestionEmbeddingProperties;

    public List<SemanticRetrievalResult> retrieve(SemanticRetrievalRequest request) {
        validate(request);

        int topK = request.getTopK() != null && request.getTopK() > 0
                ? request.getTopK()
                : semanticRetrievalProperties.getTopK();
        String modelName = hasText(request.getModelName())
                ? request.getModelName()
                : ingestionEmbeddingProperties.getModelName();

        return semanticRetrievalRepository.findTopSimilarChunks(
                request.getQueryEmbedding(),
                modelName,
                ingestionEmbeddingProperties.getDimension(),
                topK
        );
    }

    private void validate(SemanticRetrievalRequest request) {
        if (request == null || request.getQueryEmbedding() == null || request.getQueryEmbedding().isEmpty()) {
            throw new IllegalArgumentException("queryEmbedding is required");
        }

        if (request.getQueryEmbedding().size() != ingestionEmbeddingProperties.getDimension()) {
            throw new IllegalArgumentException(
                    "queryEmbedding dimension "
                            + request.getQueryEmbedding().size()
                            + " does not match expected dimension "
                            + ingestionEmbeddingProperties.getDimension()
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
