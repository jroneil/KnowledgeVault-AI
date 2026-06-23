package com.kva.document_service.documents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentRowMapper rowMapper = new DocumentRowMapper();

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Document save(Document document) {
        String sql = """
            INSERT INTO documents (collection_id, title, description, status, current_version, created_by)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id, collection_id, title, description, status, current_version, created_by, created_at, updated_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            document.getCollectionId(),
            document.getTitle(),
            document.getDescription(),
            document.getStatus(),
            document.getCurrentVersion(),
            document.getCreatedBy()
        );
    }

    public Optional<Document> findById(Long id) {
        String sql = "SELECT id, collection_id, title, description, status, current_version, created_by, created_at, updated_at FROM documents WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Document> findAll() {
        String sql = "SELECT id, collection_id, title, description, status, current_version, created_by, created_at, updated_at FROM documents ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<Document> findByCollectionId(Long collectionId) {
        String sql = "SELECT id, collection_id, title, description, status, current_version, created_by, created_at, updated_at FROM documents WHERE collection_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, collectionId);
    }

    public List<Document> findByStatus(String status) {
        String sql = "SELECT id, collection_id, title, description, status, current_version, created_by, created_at, updated_at FROM documents WHERE status = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, status);
    }

    public List<Document> findByCreatedBy(Long userId) {
        String sql = "SELECT id, collection_id, title, description, status, current_version, created_by, created_at, updated_at FROM documents WHERE created_by = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    public List<Document> searchByTitle(String searchTerm) {
        String sql = """
            SELECT id, collection_id, title, description, status, current_version, created_by, created_at, updated_at
            FROM documents
            WHERE title ILIKE ? OR description ILIKE ?
            ORDER BY created_at DESC
            """;
        String searchPattern = "%" + searchTerm + "%";
        return jdbcTemplate.query(sql, rowMapper, searchPattern, searchPattern);
    }

    public Document update(Document document) {
        String sql = """
            UPDATE documents
            SET title = ?, description = ?, status = ?, current_version = ?
            WHERE id = ?
            RETURNING id, collection_id, title, description, status, current_version, created_by, created_at, updated_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            document.getTitle(),
            document.getDescription(),
            document.getStatus(),
            document.getCurrentVersion(),
            document.getId()
        );
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM documents WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM documents WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM documents";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public long countByCollectionId(Long collectionId) {
        String sql = "SELECT COUNT(*) FROM documents WHERE collection_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, collectionId);
        return count != null ? count : 0;
    }

    public long countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM documents WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, status);
        return count != null ? count : 0;
    }

    private static class DocumentRowMapper implements RowMapper<Document> {
        @Override
        public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Document.builder()
                    .id(rs.getLong("id"))
                    .collectionId(rs.getObject("collection_id", Long.class))
                    .title(rs.getString("title"))
                    .description(rs.getString("description"))
                    .status(rs.getString("status"))
                    .currentVersion(rs.getInt("current_version"))
                    .createdBy(rs.getObject("created_by", Long.class))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}