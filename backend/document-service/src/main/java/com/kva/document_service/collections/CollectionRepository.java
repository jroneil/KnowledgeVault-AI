package com.kva.document_service.collections;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class CollectionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CollectionRowMapper rowMapper = new CollectionRowMapper();

    public CollectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Collection save(Collection collection) {
        String sql = """
            INSERT INTO collections (name, description, is_active, created_by)
            VALUES (?, ?, ?, ?)
            RETURNING id, name, description, is_active, created_by, created_at, updated_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            collection.getName(),
            collection.getDescription(),
            collection.getIsActive(),
            collection.getCreatedBy()
        );
    }

    public Optional<Collection> findById(Long id) {
        String sql = "SELECT id, name, description, is_active, created_by, created_at, updated_at FROM collections WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Collection> findByName(String name) {
        String sql = "SELECT id, name, description, is_active, created_by, created_at, updated_at FROM collections WHERE name = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, name));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Collection> findAll() {
        String sql = "SELECT id, name, description, is_active, created_by, created_at, updated_at FROM collections ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<Collection> findActive() {
        String sql = "SELECT id, name, description, is_active, created_by, created_at, updated_at FROM collections WHERE is_active = true ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<Collection> findByCreatedBy(Long userId) {
        String sql = "SELECT id, name, description, is_active, created_by, created_at, updated_at FROM collections WHERE created_by = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    public Collection update(Collection collection) {
        String sql = """
            UPDATE collections
            SET name = ?, description = ?, is_active = ?
            WHERE id = ?
            RETURNING id, name, description, is_active, created_by, created_at, updated_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            collection.getName(),
            collection.getDescription(),
            collection.getIsActive(),
            collection.getId()
        );
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM collections WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM collections WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
        return count != null && count > 0;
    }

    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM collections WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM collections";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public long countActive() {
        String sql = "SELECT COUNT(*) FROM collections WHERE is_active = true";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    private static class CollectionRowMapper implements RowMapper<Collection> {
        @Override
        public Collection mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Collection.builder()
                    .id(rs.getLong("id"))
                    .name(rs.getString("name"))
                    .description(rs.getString("description"))
                    .isActive(rs.getBoolean("is_active"))
                    .createdBy(rs.getObject("created_by", Long.class))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}