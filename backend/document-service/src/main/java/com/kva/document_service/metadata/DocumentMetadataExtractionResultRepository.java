package com.kva.document_service.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DocumentMetadataExtractionResultRepository {

    private static final TypeReference<Map<String, Double>> CONFIDENCE_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<DocumentMetadataExtractionResult> rowMapper = new ExtractionResultRowMapper();

    public DocumentMetadataExtractionResultRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public DocumentMetadataExtractionResult upsert(DocumentMetadataExtractionResult result) {
        String sql = """
            INSERT INTO document_metadata_extraction_results (
                document_id, extracted_title, extracted_manufacturer, extracted_model,
                extracted_document_type, extracted_document_number, extracted_revision,
                extracted_language, extracted_publication_date, extracted_page_count,
                extracted_tags, confidence_json, source_summary, needs_review
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (document_id) DO UPDATE SET
                extracted_title = EXCLUDED.extracted_title,
                extracted_manufacturer = EXCLUDED.extracted_manufacturer,
                extracted_model = EXCLUDED.extracted_model,
                extracted_document_type = EXCLUDED.extracted_document_type,
                extracted_document_number = EXCLUDED.extracted_document_number,
                extracted_revision = EXCLUDED.extracted_revision,
                extracted_language = EXCLUDED.extracted_language,
                extracted_publication_date = EXCLUDED.extracted_publication_date,
                extracted_page_count = EXCLUDED.extracted_page_count,
                extracted_tags = EXCLUDED.extracted_tags,
                confidence_json = EXCLUDED.confidence_json,
                source_summary = EXCLUDED.source_summary,
                needs_review = EXCLUDED.needs_review
            RETURNING id, document_id, extracted_title, extracted_manufacturer, extracted_model,
                      extracted_document_type, extracted_document_number, extracted_revision,
                      extracted_language, extracted_publication_date, extracted_page_count,
                      extracted_tags, confidence_json, source_summary, needs_review,
                      created_at, updated_at
            """;

        return jdbcTemplate.queryForObject(
                sql,
                rowMapper,
                result.getDocumentId(),
                result.getExtractedTitle(),
                result.getExtractedManufacturer(),
                result.getExtractedModel(),
                result.getExtractedDocumentType(),
                result.getExtractedDocumentNumber(),
                result.getExtractedRevision(),
                result.getExtractedLanguage(),
                result.getExtractedPublicationDate(),
                result.getExtractedPageCount(),
                toSqlArray(result.getExtractedTags()),
                serializeConfidence(result.getConfidenceByField()),
                result.getSourceSummary(),
                result.isNeedsReview()
        );
    }

    public Optional<DocumentMetadataExtractionResult> findByDocumentId(Long documentId) {
        String sql = """
            SELECT id, document_id, extracted_title, extracted_manufacturer, extracted_model,
                   extracted_document_type, extracted_document_number, extracted_revision,
                   extracted_language, extracted_publication_date, extracted_page_count,
                   extracted_tags, confidence_json, source_summary, needs_review,
                   created_at, updated_at
            FROM document_metadata_extraction_results
            WHERE document_id = ?
            """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, documentId));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String serializeConfidence(Map<String, Double> confidenceByField) {
        try {
            return objectMapper.writeValueAsString(
                    confidenceByField == null ? Collections.emptyMap() : confidenceByField
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize confidence map", e);
        }
    }

    private Map<String, Double> deserializeConfidence(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, CONFIDENCE_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize confidence map", e);
        }
    }

    private SqlArrayValue toSqlArray(List<String> tags) {
        return tags == null ? null : new SqlArrayValue("text", tags.toArray());
    }

    private class ExtractionResultRowMapper implements RowMapper<DocumentMetadataExtractionResult> {
        @Override
        public DocumentMetadataExtractionResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            String tagsArray = rs.getString("extracted_tags");
            List<String> tags = null;
            if (tagsArray != null && !tagsArray.isEmpty()) {
                String cleaned = tagsArray.replaceAll("[\\{\\}]", "");
                if (!cleaned.isEmpty()) {
                    tags = List.of(cleaned.split(","));
                }
            }

            return DocumentMetadataExtractionResult.builder()
                    .id(rs.getLong("id"))
                    .documentId(rs.getLong("document_id"))
                    .extractedTitle(rs.getString("extracted_title"))
                    .extractedManufacturer(rs.getString("extracted_manufacturer"))
                    .extractedModel(rs.getString("extracted_model"))
                    .extractedDocumentType(rs.getString("extracted_document_type"))
                    .extractedDocumentNumber(rs.getString("extracted_document_number"))
                    .extractedRevision(rs.getString("extracted_revision"))
                    .extractedLanguage(rs.getString("extracted_language"))
                    .extractedPublicationDate(rs.getObject("extracted_publication_date", LocalDate.class))
                    .extractedPageCount(rs.getObject("extracted_page_count", Integer.class))
                    .extractedTags(tags)
                    .confidenceByField(deserializeConfidence(rs.getString("confidence_json")))
                    .sourceSummary(rs.getString("source_summary"))
                    .needsReview(rs.getBoolean("needs_review"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
