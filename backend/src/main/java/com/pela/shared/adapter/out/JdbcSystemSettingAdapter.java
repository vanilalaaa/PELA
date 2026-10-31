package com.pela.shared.adapter.out;

import com.pela.shared.application.port.out.SystemSettingPort;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcSystemSettingAdapter implements SystemSettingPort {

    private final JdbcTemplate jdbc;

    public JdbcSystemSettingAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> text(String key) {
        // #>> '{}' trả giá trị vô hướng không kèm dấu nháy JSON
        return jdbc.queryForList("SELECT value #>> '{}' FROM system_setting WHERE setting_key = ?", String.class, key)
                .stream().findFirst();
    }
}
