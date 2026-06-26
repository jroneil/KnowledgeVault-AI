package com.kva.document_service.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentEmbeddingService {

    private final DocumentEmbeddingClient documentEmbeddingClient;
    private final DocumentEmbeddingRepository documentEmbeddingRepository;
    private final IngestionEmbeddingProperties ingestionEmbeddingProperties;

    public EmbeddingRunResult generateAndPersistEmbeddings(List<DocumentChunk> chunks) {
        if (!ingestionEmbeddingProperties.isEnabled() || chunks == null || chunks.isEmpty()) {
            return EmbeddingRunResult.success(0);
        }

        int totalEmbeddings = 0;
        int batchSize = Math.max(1, ingestionEmbeddingProperties.getBatchSize());

        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            List<DocumentChunk> batch = chunks.subList(start, end);

            EmbeddingBatchResult batchResult = documentEmbeddingClient.generateEmbeddings(
                    GenerateEmbeddingsBatchRequest.builder()
                            .texts(batch.stream().map(DocumentChunk::getContent).toList())
                            .model(ingestionEmbeddingProperties.getModelName())
                            .build()
            );

            if (!batchResult.isSuccess()) {
                return EmbeddingRunResult.failure(
                        batchResult.getErrorCode(),
                        batchResult.getErrorMessage(),
                        batchResult.isRetryable()
                );
            }

            List<List<Double>> embeddings = batchResult.getEmbeddings();
            if (embeddings == null || embeddings.size() != batch.size()) {
                return EmbeddingRunResult.failure(
                        "EMBEDDING_BATCH_SIZE_MISMATCH",
                        "Embedding provider returned an unexpected number of embeddings",
                        false
                );
            }

            List<DocumentEmbeddingRecord> records = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                List<Double> embedding = embeddings.get(i);
                if (embedding == null || embedding.size() != ingestionEmbeddingProperties.getDimension()) {
                    return EmbeddingRunResult.failure(
                            "EMBEDDING_DIMENSION_MISMATCH",
                            "Embedding model returned "
                                    + (embedding == null ? 0 : embedding.size())
                                    + " dimensions; expected "
                                    + ingestionEmbeddingProperties.getDimension(),
                            false
                    );
                }

                records.add(DocumentEmbeddingRecord.builder()
                        .chunkId(batch.get(i).getId())
                        .modelName(batchResult.getModelName() == null
                                ? ingestionEmbeddingProperties.getModelName()
                                : batchResult.getModelName())
                        .modelVersion(batchResult.getModelVersion())
                        .embedding(embedding)
                        .dimension(embedding.size())
                        .build());
            }

            documentEmbeddingRepository.saveBatch(records);
            totalEmbeddings += records.size();
        }

        return EmbeddingRunResult.success(totalEmbeddings);
    }
}
