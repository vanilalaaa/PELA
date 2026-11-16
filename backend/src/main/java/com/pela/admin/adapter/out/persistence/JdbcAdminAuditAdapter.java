package com.pela.admin.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pela.shared.application.port.out.AdminAuditPort;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.IdGeneratorPort;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** BR-35: admin_action_log chỉ INSERT; không có thao tác sửa/xoá. */
@Component
public class JdbcAdminAuditAdapter implements AdminAuditPort {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final IdGeneratorPort ids;

    public JdbcAdminAuditAdapter(JdbcTemplate jdbc, ClockPort clock, IdGeneratorPort ids) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public void record(UUID adminId, String actionType, String targetEntity, String targetId, Object detail) {
        String json;
        try {
            json = detail == null ? null : JSON.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit detail is not serializable", e);
        }
        jdbc.update("""
                INSERT INTO admin_action_log (log_id, admin_id, action_type, target_entity, target_id, detail, acted_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """, ids.newId(), adminId, actionType, targetEntity, targetId, json, Timestamp.from(clock.now()));
    }
}
