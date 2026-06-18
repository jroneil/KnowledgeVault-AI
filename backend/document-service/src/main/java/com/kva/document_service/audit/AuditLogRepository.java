package com.kva.document_service.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogRowMapper rowMapper = new AuditLogRowMapper();

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuditLog save(AuditLog auditLog) {
        String sql = """
            INSERT INTO audit_log (user_id, action, entity_type, entity_id, details, ip_address, user_agent)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
            RETURNING id, user_id, action, entity_type, entity_id, details, ip_address, user_agent, created_at
            """;
        
        String jsonbDetails = auditLog.getDetails() != null ? 
            auditLog.getDetails().toString() : null;
        
        return jdbcTemplate.queryForObject(sql, rowMapper,
            auditLog.getUserId(),
            auditLog.getAction(),
            auditLog.getEntityType(),
            auditLog.getEntityId(),
            jsonbDetails,
            auditLog.getIpAddress(),
            auditLog.getUserAgent()
        );
    }

    public List<AuditLog> findByUserId(Long userId, int limit) {
        String sql = """
            SELECT id, user_id, action, entity_type, entity_id, details, ip_address, user_agent, created_at
            FROM audit_log WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
            """;
        return jdbcTemplate.query(sql, rowMapper, userId, limit);
    }

    private static class AuditLogRowMapper implements RowMapper<AuditLog> {
        @Override
        public AuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AuditLog.builder()
                    .id(rs.getLong("id"))
                    .userId(rs.getObject("user_id", Long.class))
                    .action(rs.getString("action"))
                    .entityType(rs.getString("entity_type"))
                    .entityId(rs.getObject("entity_id", Long.class))
                    .ipAddress(rs.getString("ip_address"))
                    .userAgent(rs.getString("user_agent"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .build();
        }
    }
}