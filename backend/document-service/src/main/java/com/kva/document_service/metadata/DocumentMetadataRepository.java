package com.kva.document_service.metadata;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentMetadataRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentMetadataRowMapper rowMapper = new DocumentMetadataRowMapper();

    public DocumentMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DocumentMetadata save(DocumentMetadata metadata) {
        String sql = """
            INSERT INTO document_metadata (
                document_id, product, revision, department, manufacturer, model, document_type,
                document_number, language, tags, category, effective_date, publication_date, page_count
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, document_id, product, revision, department, manufacturer, model, document_type,
                      document_number, language, tags, category, effective_date, publication_date, page_count,
                      created_at, updated_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            metadata.getDocumentId(),
            metadata.getProduct(),
            metadata.getRevision(),
            metadata.getDepartment(),
            metadata.getManufacturer(),
            metadata.getModel(),
            metadata.getDocumentType(),
            metadata.getDocumentNumber(),
            metadata.getLanguage(),
            toSqlArray(metadata.getTags()),
            metadata.getCategory(),
            metadata.getEffectiveDate(),
            metadata.getPublicationDate(),
            metadata.getPageCount()
        );
    }

    public Optional<DocumentMetadata> findById(Long id) {
        String sql = "SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at FROM document_metadata WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<DocumentMetadata> findByDocumentId(Long documentId) {
        String sql = "SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at FROM document_metadata WHERE document_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, documentId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<DocumentMetadata> findAll() {
        String sql = "SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at FROM document_metadata ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<DocumentMetadata> findByProduct(String product) {
        String sql = "SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at FROM document_metadata WHERE product = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, product);
    }

    public List<DocumentMetadata> findByDepartment(String department) {
        String sql = "SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at FROM document_metadata WHERE department = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, department);
    }

    public List<DocumentMetadata> findByCategory(String category) {
        String sql = "SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at FROM document_metadata WHERE category = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, category);
    }

    public List<DocumentMetadata> findByTag(String tag) {
        String sql = "SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at FROM document_metadata WHERE ? = ANY(tags) ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, tag);
    }

    public List<DocumentMetadata> searchByMetadata(String searchTerm) {
        String sql = """
            SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at
            FROM document_metadata
            WHERE product ILIKE ? OR revision ILIKE ? OR department ILIKE ? OR manufacturer ILIKE ? OR category ILIKE ? OR model ILIKE ? OR document_number ILIKE ?
            ORDER BY created_at DESC
            """;
        String searchPattern = "%" + searchTerm + "%";
        return jdbcTemplate.query(sql, rowMapper, searchPattern, searchPattern, searchPattern, searchPattern, searchPattern, searchPattern, searchPattern);
    }

    public DocumentMetadata update(DocumentMetadata metadata) {
        String sql = """
            UPDATE document_metadata
            SET product = ?, revision = ?, department = ?, manufacturer = ?, model = ?, document_type = ?,
                document_number = ?, language = ?, tags = ?, category = ?, effective_date = ?, publication_date = ?, page_count = ?
            WHERE id = ?
            RETURNING id, document_id, product, revision, department, manufacturer, model, document_type,
                      document_number, language, tags, category, effective_date, publication_date, page_count,
                      created_at, updated_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            metadata.getProduct(),
            metadata.getRevision(),
            metadata.getDepartment(),
            metadata.getManufacturer(),
            metadata.getModel(),
            metadata.getDocumentType(),
            metadata.getDocumentNumber(),
            metadata.getLanguage(),
            toSqlArray(metadata.getTags()),
            metadata.getCategory(),
            metadata.getEffectiveDate(),
            metadata.getPublicationDate(),
            metadata.getPageCount(),
            metadata.getId()
        );
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM document_metadata WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM document_metadata WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    public boolean existsByDocumentId(Long documentId) {
        String sql = "SELECT COUNT(*) FROM document_metadata WHERE document_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, documentId);
        return count != null && count > 0;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM document_metadata";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public List<DocumentMetadata> findByFilters(String product, String revision, String department, 
                                               String manufacturer, String category, List<String> tags) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, document_id, product, revision, department, manufacturer, model, document_type, document_number, language, tags, category, effective_date, publication_date, page_count, created_at, updated_at " +
            "FROM document_metadata WHERE 1=1"
        );
        
        List<Object> params = new ArrayList<>();
        
        if (product != null && !product.isEmpty()) {
            sql.append(" AND product = ?");
            params.add(product);
        }
        if (revision != null && !revision.isEmpty()) {
            sql.append(" AND revision = ?");
            params.add(revision);
        }
        if (department != null && !department.isEmpty()) {
            sql.append(" AND department = ?");
            params.add(department);
        }
        if (manufacturer != null && !manufacturer.isEmpty()) {
            sql.append(" AND manufacturer = ?");
            params.add(manufacturer);
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (tags != null && !tags.isEmpty()) {
            sql.append(" AND tags && ?::text[]");
            params.add(toSqlArray(tags));
        }
        
        sql.append(" ORDER BY created_at DESC");
        
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    private SqlArrayValue toSqlArray(List<String> tags) {
        return tags == null ? null : new SqlArrayValue("text", tags.toArray());
    }

    private static class DocumentMetadataRowMapper implements RowMapper<DocumentMetadata> {
        @Override
        public DocumentMetadata mapRow(ResultSet rs, int rowNum) throws SQLException {
            // Convert PostgreSQL array to Java List
            String tagsArray = rs.getString("tags");
            List<String> tags = null;

            if (tagsArray != null && !tagsArray.isEmpty()) {
                // Remove curly braces and split by comma
                String cleaned = tagsArray.replaceAll("[\\{\\}]", "");
                if (!cleaned.isEmpty()) {
                    tags = List.of(cleaned.split(","));
                }
            }

            return DocumentMetadata.builder()
                    .id(rs.getLong("id"))
                    .documentId(rs.getLong("document_id"))
                    .product(rs.getString("product"))
                    .revision(rs.getString("revision"))
                    .department(rs.getString("department"))
                    .manufacturer(rs.getString("manufacturer"))
                    .model(rs.getString("model"))
                    .documentType(rs.getString("document_type"))
                    .documentNumber(rs.getString("document_number"))
                    .language(rs.getString("language"))
                    .tags(tags)
                    .category(rs.getString("category"))
                    .effectiveDate(rs.getObject("effective_date", LocalDate.class))
                    .publicationDate(rs.getObject("publication_date", LocalDate.class))
                    .pageCount(rs.getObject("page_count", Integer.class))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
