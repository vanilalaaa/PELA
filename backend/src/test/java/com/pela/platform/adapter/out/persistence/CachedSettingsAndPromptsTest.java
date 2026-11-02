package com.pela.platform.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pela.shared.application.port.out.SettingsPort;
import com.pela.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CachedSettingsAndPromptsTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T08:00:00Z"));

    @Test
    void settingsAreCachedForFiveMinutesIncludingMissingKeys() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("ai.model.text")))
                .thenReturn(List.of("\"gemini-2.5-flash\""), List.of("\"gemini-new\""));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("missing"))).thenReturn(List.of());
        var settings = new JdbcSettingsAdapter(jdbc, clock);

        assertThat(settings.requiredString("ai.model.text")).isEqualTo("gemini-2.5-flash");
        assertThat(settings.string("missing")).isEmpty();
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(settings.requiredString("ai.model.text")).isEqualTo("gemini-2.5-flash");
        assertThat(settings.string("missing")).isEmpty();
        verify(jdbc, times(1)).queryForList(anyString(), eq(String.class), eq("ai.model.text"));
        verify(jdbc, times(1)).queryForList(anyString(), eq(String.class), eq("missing"));

        clock.advance(Duration.ofSeconds(1));
        assertThat(settings.requiredString("ai.model.text")).isEqualTo("gemini-new");
    }

    @Test
    void typedSettingValues() {
        Map<String, String> raw = Map.of(
                "int", "5",
                "double", "0.65",
                "text", "\"BLOCK_LOW_AND_ABOVE\"",
                "numberAsText", "\"30\"",
                "object", "{\"A1\":\"Speak slowly\",\"B1\":\"Natural pace\"}",
                "array", "[{\"min_score\":90,\"stars\":5}]");
        SettingsPort settings = key -> Optional.ofNullable(raw.get(key));

        assertThat(settings.requiredInt("int")).isEqualTo(5);
        assertThat(settings.requiredDouble("double")).isEqualTo(0.65);
        assertThat(settings.requiredString("text")).isEqualTo("BLOCK_LOW_AND_ABOVE");
        assertThat(settings.requiredInt("numberAsText")).isEqualTo(30);
        assertThat(settings.requiredJson("object", Map.class)).containsEntry("B1", "Natural pace");
        assertThat(settings.requiredJson("array", WritingBand[].class)).containsExactly(new WritingBand(90, 5));
        assertThat(settings.intValue("none")).isEmpty();
        assertThatThrownBy(() -> settings.requiredString("none")).hasMessageContaining("none");
    }

    record WritingBand(int min_score, int stars) {
    }
}
