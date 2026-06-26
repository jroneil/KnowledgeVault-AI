package com.kva.document_service.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DocumentEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveBatch(List<DocumentEmbeddingRecord> embeddings) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO embeddings (chunk_id, model_name, model_version, embedding, dimension, created_at, updated_at)
                VALUES (?, ?, ?, CAST(? AS vector), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (chunk_id, model_name)
                DO UPDATE SET
                    model_version = EXCLUDED.model_version,
                    embedding = EXCLUDED.embedding,
                    dimension = EXCLUDED.dimension,
                    updated_at = CURRENT_TIMESTAMP
                """,
                embeddings,
                embeddings.size(),
                (PreparedStatement ps, DocumentEmbeddingRecord record) -> {
                    ps.setLong(1, record.getChunkId());
                    ps.setString(2, record.getModelName());
                    if (record.getModelVersion() == null || record.getModelVersion().isBlank()) {
                        ps.setNull(3, Types.VARCHAR);
                    } else {
                        ps.setString(3, record.getModelVersion());
                    }
                    ps.setString(4, toVectorLiteral(record.getEmbedding()));
                    ps.setInt(5, record.getDimension());
                }
        );
    }

    private String toVectorLiteral(List<Double> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values.get(i));
        }
        return builder.append(']').toString();
    }
}
