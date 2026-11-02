package com.pela.onboarding.adapter.out.persistence;

import com.pela.onboarding.application.port.out.LearnerProfilePort;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcLearnerProfileAdapter implements LearnerProfilePort {

    private final JdbcTemplate jdbc;

    public JdbcLearnerProfileAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> interests(UUID userId) {
        return jdbc.query("SELECT interests FROM learner_profile WHERE user_id = ?", rs -> {
            if (!rs.next()) {
                return List.<String>of();
            }
            Array array = rs.getArray("interests");
            return array == null ? List.<String>of() : toList(array);
        }, userId);
    }

    private static List<String> toList(Array array) throws SQLException {
        return Arrays.stream((String[]) array.getArray()).toList();
    }
}
