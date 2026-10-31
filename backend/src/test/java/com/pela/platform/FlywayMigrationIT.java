package com.pela.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pela.support.PostgresIntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Migrate sạch stub → V1 → V2 → V3 (không có seed dev). Test ghi dữ liệu đều rollback. */
class FlywayMigrationIT extends PostgresIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsAppliedSuccessfully() {
        MigrationInfo[] all = flyway.info().all();

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(all)
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
        assertThat(Arrays.stream(all).map(m -> m.getVersion().getVersion()))
                .containsExactly("0.1", "1", "2", "3", "4");
    }

    @Test
    void v4AddsLockoutWindowAndSettings() {
        assertThat(columns("users")).contains("failed_login_window_started_at");
        assertThat(setting("auth.lockout_max_attempts")).isEqualTo("5");
        assertThat(setting("auth.lockout_minutes")).isEqualTo("15");
    }

    @Test
    void questionBankHasVectorColumn() {
        String type = jdbc.queryForObject("""
                SELECT udt_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'question_bank'
                  AND column_name = 'embedding_vector'
                """, String.class);
        Integer dimensions = jdbc.queryForObject("""
                SELECT atttypmod FROM pg_attribute
                WHERE attrelid = 'public.question_bank'::regclass AND attname = 'embedding_vector'
                """, Integer.class);

        assertThat(type).isEqualTo("vector");
        assertThat(dimensions).isEqualTo(768);
    }

    @Test
    void v3AddsPromptAndListeningTtsColumns() {
        assertThat(columns("prompt_template")).contains("system_text", "response_schema", "temperature", "note");
        assertThat(columns("listening_clip")).contains("voice_config", "tts_model", "audio_format");
        String voiceConfigDefault = jdbc.queryForObject("""
                SELECT column_default || ' nullable=' || is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'listening_clip' AND column_name = 'voice_config'
                """, String.class);
        assertThat(voiceConfigDefault).isEqualTo("'{}'::jsonb nullable=NO");
    }

    @Test
    void v3SeedsActivePlaceholderPrompts() {
        List<String> active = jdbc.queryForList(
                "SELECT name FROM prompt_template WHERE is_active AND version = 1 AND body = 'TODO v1' ORDER BY name",
                String.class);
        assertThat(active).containsExactly("rag_question_generation", "writing_file_extraction", "writing_grading");
    }

    @Test
    @Transactional
    void onlyOneActivePromptPerName() {
        // Bản không active thêm được thoải mái
        jdbc.update("INSERT INTO prompt_template (name, version, body, is_active) VALUES ('writing_grading', 2, 'draft', false)");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO prompt_template (name, version, body, is_active) VALUES ('writing_grading', 3, 'x', true)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_prompt_active");
    }

    @Test
    @Transactional
    void importJobAcceptsListeningCsvKind() {
        UUID adminId = UUID.randomUUID();
        // Trigger trên auth.users (V1) tự tạo public.users
        jdbc.update("INSERT INTO auth.users (id, email) VALUES (?, 'admin@example.test')", adminId);

        jdbc.update("""
                INSERT INTO import_job (kind, files, total_bytes, created_by)
                VALUES ('listening_csv', '[{"path":"imports/sample.csv","bytes":10}]', 10, ?)
                """, adminId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_job WHERE kind = 'listening_csv'", Integer.class))
                .isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO import_job (kind, files, total_bytes, created_by)
                VALUES ('unknown_kind', '[]', 10, ?)
                """, adminId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("import_job_kind_check");
    }

    @Test
    void v3AddsAiAndListeningSettings() {
        List<String> keys = jdbc.queryForList("SELECT setting_key FROM system_setting", String.class);
        assertThat(keys).contains("ai.model.text", "ai.model.embedding", "ai.embedding.dimensions", "ai.model.tts",
                "ai.tts.sample_rate", "ai.tts.style_by_level", "listening.max_speakers", "listening.max_chars",
                "star.writing_bands", "ai.safety.block_threshold");

        assertThat(setting("ai.model.text")).isEqualTo("\"gemini-2.5-flash\"");
        assertThat(setting("ai.model.tts")).isEqualTo("\"\"");
        assertThat(setting("ai.embedding.dimensions")).isEqualTo("768");
        assertThat(jdbc.queryForObject(
                "SELECT value->'B1' #>> '{}' FROM system_setting WHERE setting_key = 'ai.tts.style_by_level'", String.class))
                .isEqualTo("Natural pace");
        assertThat(jdbc.queryForObject(
                "SELECT jsonb_array_length(value) FROM system_setting WHERE setting_key = 'star.writing_bands'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT description FROM system_setting WHERE setting_key = 'import.max_audio_mb'", String.class))
                .startsWith("Không dùng từ SRS 4.2");
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, table);
    }

    private String setting(String key) {
        return jdbc.queryForObject("SELECT value::text FROM system_setting WHERE setting_key = ?", String.class, key);
    }
}
