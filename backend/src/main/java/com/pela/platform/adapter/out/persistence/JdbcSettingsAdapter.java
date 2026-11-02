package com.pela.platform.adapter.out.persistence;

import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.SettingsPort;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Bảng system_setting, cache {@link #CACHE_TTL}. */
@Component
public class JdbcSettingsAdapter implements SettingsPort {

    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final TtlCache<String> cache;

    public JdbcSettingsAdapter(JdbcTemplate jdbc, ClockPort clock) {
        this.jdbc = jdbc;
        this.cache = new TtlCache<>(CACHE_TTL, clock);
    }

    @Override
    public Optional<String> rawJson(String key) {
        return cache.get(key, k -> jdbc.queryForList(
                "SELECT value::text FROM system_setting WHERE setting_key = ?", String.class, k).stream().findFirst());
    }
}
