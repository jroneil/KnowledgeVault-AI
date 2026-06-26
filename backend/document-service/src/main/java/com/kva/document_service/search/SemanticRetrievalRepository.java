package com.kva.document_service.search;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SemanticRetrievalRepository {

    private static final RowMapper<SemanticRetrievalResult> RESULT_ROW_MAPPER = SemanticRetrievalRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public List<SemanticRetrievalResult> findTopSimilarChunks(List<Double> queryEmbedding,
                                                              String modelName,
                                                              int expectedDimension,
                                                              int topK) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);

        return jdbcTemplate.query(
                """
                SELECT
                    c.id AS chunk_id,
                    c.document_id,
                    c.version_id,
                    dv.version_number,
                    c.chunk_index,
                    c.content,
                    c.page_number,
                    c.source_page_from,
                    c.source_page_to,
                    c.section_name,
                    c.token_count,
                    1 - (e.embedding <=> CAST(? AS vector)) AS similarity_score
                FROM embeddings e
                JOIN document_chunks c ON c.id = e.chunk_id
                LEFT JOIN document_versions dv ON dv.id = c.version_id
                WHERE e.model_name = ?
                  AND e.dimension = ?
                ORDER BY e.embedding <=> CAST(? AS vector) ASC, c.id ASC
                LIMIT ?
                """,
                RESULT_ROW_MAPPER,
                vectorLiteral,
                modelName,
                expectedDimension,
                vectorLiteral,
                topK
        );
    }

    private static SemanticRetrievalResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SemanticRetrievalResult.builder()
                .chunkId(rs.getLong("chunk_id"))
                .documentId(rs.getLong("document_id"))
                .versionId(getNullableLong(rs, "version_id"))
                .versionNumber(getNullableInteger(rs, "version_number"))
                .chunkIndex(getNullableInteger(rs, "chunk_index"))
                .content(rs.getString("content"))
                .pageNumber(getNullableInteger(rs, "page_number"))
                .sourcePageFrom(getNullableInteger(rs, "source_page_from"))
                .sourcePageTo(getNullableInteger(rs, "source_page_to"))
                .sectionName(rs.getString("section_name"))
                .tokenCount(getNullableInteger(rs, "token_count"))
                .similarityScore(getNullableDouble(rs, "similarity_score"))
                .build();
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
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
