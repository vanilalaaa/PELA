package com.pela.platform.adapter.out.persistence;

import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.PromptTemplatePort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Bảng prompt_template (bản is_active của mỗi name), cache {@link #CACHE_TTL}. */
@Component
public class JdbcPromptTemplateAdapter implements PromptTemplatePort {

    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final TtlCache<PromptTemplate> cache;

    public JdbcPromptTemplateAdapter(JdbcTemplate jdbc, ClockPort clock) {
        this.jdbc = jdbc;
        this.cache = new TtlCache<>(CACHE_TTL, clock);
    }

    @Override
    public Optional<PromptTemplate> active(String name) {
        return cache.get(name, n -> jdbc.query("""
                        SELECT name, version, system_text, body, response_schema::text AS response_schema, temperature
                        FROM prompt_template WHERE name = ? AND is_active
                        """,
                (rs, i) -> {
                    BigDecimal temperature = rs.getBigDecimal("temperature");
                    return new PromptTemplate(rs.getString("name"), rs.getInt("version"), rs.getString("system_text"),
                            rs.getString("body"), rs.getString("response_schema"),
                            temperature == null ? null : temperature.doubleValue());
                }, n).stream().findFirst());
    }
}
