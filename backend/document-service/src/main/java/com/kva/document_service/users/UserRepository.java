package com.kva.document_service.users;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final UserRowMapper rowMapper = new UserRowMapper();

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User save(User user) {
        String sql = """
            INSERT INTO users (username, email, password_hash, first_name, last_name, status)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id, username, email, password_hash, first_name, last_name, status, created_at, updated_at, last_login_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            user.getUsername(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getFirstName(),
            user.getLastName(),
            user.getStatus()
        );
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT id, username, email, password_hash, first_name, last_name, status, created_at, updated_at, last_login_at FROM users WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, email, password_hash, first_name, last_name, status, created_at, updated_at, last_login_at FROM users WHERE username = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, username));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, username, email, password_hash, first_name, last_name, status, created_at, updated_at, last_login_at FROM users WHERE email = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, email));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<User> findAll() {
        String sql = "SELECT id, username, email, password_hash, first_name, last_name, status, created_at, updated_at, last_login_at FROM users ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public User update(User user) {
        String sql = """
            UPDATE users
            SET username = ?, email = ?, first_name = ?, last_name = ?, status = ?, last_login_at = ?
            WHERE id = ?
            RETURNING id, username, email, password_hash, first_name, last_name, status, created_at, updated_at, last_login_at
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper,
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getStatus(),
            user.getLastLoginAt(),
            user.getId()
        );
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public List<String> findRolesByUserId(Long userId) {
        String sql = "SELECT r.name FROM roles r JOIN user_roles ur ON r.id = ur.role_id WHERE ur.user_id = ?";
        return jdbcTemplate.queryForList(sql, String.class, userId);
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            return User.builder()
                    .id(rs.getLong("id"))
                    .username(rs.getString("username"))
                    .email(rs.getString("email"))
                    .passwordHash(rs.getString("password_hash"))
                    .firstName(rs.getString("first_name"))
                    .lastName(rs.getString("last_name"))
                    .status(rs.getString("status"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .lastLoginAt(rs.getTimestamp("last_login_at") != null ? 
                        rs.getTimestamp("last_login_at").toLocalDateTime() : null)
                    .build();
        }
    }
}