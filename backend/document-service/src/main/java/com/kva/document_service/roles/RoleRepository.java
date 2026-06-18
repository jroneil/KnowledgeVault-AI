package com.kva.document_service.roles;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RoleRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RoleRowMapper rowMapper = new RoleRowMapper();

    public RoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Role save(Role role) {
        String sql = """
            INSERT INTO roles (name, description)
            VALUES (?, ?)
            RETURNING id, name, description, created_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper, role.getName(), role.getDescription());
    }

    public Optional<Role> findById(Long id) {
        String sql = "SELECT id, name, description, created_at FROM roles WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Role> findByName(String name) {
        String sql = "SELECT id, name, description, created_at FROM roles WHERE name = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, name));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Role> findAll() {
        String sql = "SELECT id, name, description, created_at FROM roles ORDER BY name";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public void assignRoleToUser(Long userId, Long roleId) {
        String sql = "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        jdbcTemplate.update(sql, userId, roleId);
    }

    public void removeRoleFromUser(Long userId, Long roleId) {
        String sql = "DELETE FROM user_roles WHERE user_id = ? AND role_id = ?";
        jdbcTemplate.update(sql, userId, roleId);
    }

    private static class RoleRowMapper implements RowMapper<Role> {
        @Override
        public Role mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Role.builder()
                    .id(rs.getLong("id"))
                    .name(rs.getString("name"))
                    .description(rs.getString("description"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .build();
        }
    }
}