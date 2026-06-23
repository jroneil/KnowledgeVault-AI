package com.kva.document_service.documents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentVersionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentVersionRowMapper rowMapper = new DocumentVersionRowMapper();

    public DocumentVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DocumentVersion save(DocumentVersion version) {
        String sql = """
            INSERT INTO document_versions (document_id, version_number, file_name, file_path, file_size, mime_type, uploaded_by, is_current)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, document_id, version_number, file_name, file_path, file_size, mime_type, uploaded_by, upload_date, is_current
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            version.getDocumentId(),
            version.getVersionNumber(),
            version.getFileName(),
            version.getFilePath(),
            version.getFileSize(),
            version.getMimeType(),
            version.getUploadedBy(),
            version.getIsCurrent()
        );
    }

    public Optional<DocumentVersion> findById(Long id) {
        String sql = "SELECT id, document_id, version_number, file_name, file_path, file_size, mime_type, uploaded_by, upload_date, is_current FROM document_versions WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<DocumentVersion> findByDocumentId(Long documentId) {
        String sql = "SELECT id, document_id, version_number, file_name, file_path, file_size, mime_type, uploaded_by, upload_date, is_current FROM document_versions WHERE document_id = ? ORDER BY version_number DESC";
        return jdbcTemplate.query(sql, rowMapper, documentId);
    }

    public Optional<DocumentVersion> findCurrentVersion(Long documentId) {
        String sql = "SELECT id, document_id, version_number, file_name, file_path, file_size, mime_type, uploaded_by, upload_date, is_current FROM document_versions WHERE document_id = ? AND is_current = true";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, documentId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber) {
        String sql = "SELECT id, document_id, version_number, file_name, file_path, file_size, mime_type, uploaded_by, upload_date, is_current FROM document_versions WHERE document_id = ? AND version_number = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, documentId, versionNumber));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<DocumentVersion> findAll() {
        String sql = "SELECT id, document_id, version_number, file_name, file_path, file_size, mime_type, uploaded_by, upload_date, is_current FROM document_versions ORDER BY upload_date DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public DocumentVersion update(DocumentVersion version) {
        String sql = """
            UPDATE document_versions
            SET is_current = ?
            WHERE id = ?
            RETURNING id, document_id, version_number, file_name, file_path, file_size, mime_type, uploaded_by, upload_date, is_current
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            version.getIsCurrent(),
            version.getId()
        );
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM document_versions WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void markPreviousVersionsNotCurrent(Long documentId, Long currentVersionId) {
        String sql = """
            UPDATE document_versions
            SET is_current = false
            WHERE document_id = ? AND id != ?
            """;
        jdbcTemplate.update(sql, documentId, currentVersionId);
    }

    public void markPreviousVersionsNotCurrent(Long documentId) {
        String sql = """
            UPDATE document_versions
            SET is_current = false
            WHERE document_id = ?
            """;
        jdbcTemplate.update(sql, documentId);
    }

    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM document_versions WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM document_versions";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public long countByDocumentId(Long documentId) {
        String sql = "SELECT COUNT(*) FROM document_versions WHERE document_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, documentId);
        return count != null ? count : 0;
    }

    private static class DocumentVersionRowMapper implements RowMapper<DocumentVersion> {
        @Override
        public DocumentVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
            return DocumentVersion.builder()
                    .id(rs.getLong("id"))
                    .documentId(rs.getLong("document_id"))
                    .versionNumber(rs.getInt("version_number"))
                    .fileName(rs.getString("file_name"))
                    .filePath(rs.getString("file_path"))
                    .fileSize(rs.getLong("file_size"))
                    .mimeType(rs.getString("mime_type"))
                    .uploadedBy(rs.getObject("uploaded_by", Long.class))
                    .uploadDate(rs.getTimestamp("upload_date").toLocalDateTime())
                    .isCurrent(rs.getBoolean("is_current"))
                    .build();
        }
    }
}