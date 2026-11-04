package com.pela.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.support.PgvectorContainer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Seed dev 138 đề Writing (db/seed/R__dev_seed_writing_prompts.sql, sinh từ docs/content/writing).
 * Không dựng Spring context: chạy Flyway (stub → migrations → seed) trên database riêng rồi kiểm dữ liệu.
 * Chạy lại seed bằng ScriptUtils để mô phỏng việc file seed đổi nội dung.
 */
@Testcontainers(disabledWithoutDocker = true)
class DevWritingPromptSeedIT {

    private static final String SEED_FILE = "../db/seed/R__dev_seed_writing_prompts.sql";
    /** 9b000000-0000-4000-8000-0LLNNN000000, LL = 01..06: dải riêng, không trùng 9a…/9b…0101 của R__dev_seed.sql. */
    private static final String SEED_IDS = "question_id::text ~ '^9b000000-0000-4000-8000-0(0[1-6])[0-9]{3}000000$'";
    private static final String ZERO_VECTOR = "array_fill(0.1::real, ARRAY[768])::vector";

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static Flyway flyway;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(PgvectorContainer.jdbcUrlFor("writing_prompt_seed"),
                PgvectorContainer.instance().getUsername(), PgvectorContainer.instance().getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/stub", "filesystem:../db/migrations", "filesystem:../db/seed")
                .load();
        flyway.migrate();
    }

    /** Mỗi test bắt đầu từ trạng thái mới nạp: xoá 138 đề rồi chạy lại đúng file seed. */
    @BeforeEach
    void freshSeed() throws SQLException {
        jdbc.update("DELETE FROM question_bank WHERE " + SEED_IDS);
        runSeed();
    }

    @Test
    void flywayAppliedSeedAfterTheAdminSeed() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().all()).extracting(MigrationInfo::getState).containsOnly(MigrationState.SUCCESS);
        List<String> repeatable = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getScript).filter(script -> script.startsWith("R__")).toList();
        assertThat(repeatable).containsExactlyInAnyOrder("R__dev_seed.sql", "R__dev_seed_writing_prompts.sql");
    }

    @Test
    void seedsAll138PromptsAsRetiredWithoutVector() {
        assertThat(count("SELECT count(*) FROM question_bank WHERE " + SEED_IDS)).isEqualTo(138);
        assertThat(count("""
                SELECT count(*) FROM question_bank
                WHERE %s AND question_purpose = 'writing_prompt' AND status = 'retired'
                  AND embedding_vector IS NULL AND embedding_model IS NULL AND retired_at IS NULL
                """.formatted(SEED_IDS))).isEqualTo(138);

        Map<String, Integer> perLevel = new LinkedHashMap<>();
        jdbc.query("SELECT cefr_level, count(*) AS n FROM question_bank WHERE " + SEED_IDS
                + " GROUP BY cefr_level ORDER BY cefr_level",
                rs -> {
                    perLevel.put(rs.getString("cefr_level"), rs.getInt("n"));
                });
        assertThat(perLevel).containsExactly(Map.entry("A1", 28), Map.entry("A2", 25), Map.entry("B1", 25),
                Map.entry("B2", 20), Map.entry("C1", 20), Map.entry("C2", 20));

        // Mọi đề có khoảng từ hợp lệ theo Writing.Submit.Limit
        assertThat(count("""
                SELECT count(*) FROM question_bank
                WHERE %s AND ((payload->>'min_words')::int < 20
                           OR (payload->>'max_words')::int > 300
                           OR (payload->>'min_words')::int >= (payload->>'max_words')::int)
                """.formatted(SEED_IDS))).isZero();
        // Đề của Văn (9a000000-…) không bị đụng tới
        assertThat(count("SELECT count(*) FROM question_bank WHERE question_id::text LIKE '9a000000-%'")).isEqualTo(12);
    }

    @Test
    void rerunningUnchangedSeedDuplicatesNothingAndKeepsEmbeddedPrompts() throws SQLException {
        String id = firstId("A1");
        activate(id);

        runSeed();

        assertThat(count("SELECT count(*) FROM question_bank WHERE " + SEED_IDS)).isEqualTo(138);
        assertThat(jdbc.queryForObject(
                "SELECT status || '/' || (embedding_vector IS NOT NULL) || '/' || embedding_model FROM question_bank"
                        + " WHERE question_id = ?::uuid", String.class, id)).isEqualTo("active/true/test-model");
    }

    @Test
    void changedTextOrPayloadResetsEmbeddingSoBackfillEmbedsAgain() throws SQLException {
        String textChanged = firstId("B1");
        String payloadChanged = firstId("C2");
        String untouched = firstId("A2");
        for (String id : List.of(textChanged, payloadChanged, untouched)) {
            activate(id);
        }
        String seedText = jdbc.queryForObject("SELECT question_text FROM question_bank WHERE question_id = ?::uuid",
                String.class, textChanged);
        String seedMaxWords = jdbc.queryForObject(
                "SELECT payload->>'max_words' FROM question_bank WHERE question_id = ?::uuid", String.class,
                payloadChanged);
        // Mô phỏng file seed đổi: bản trong DB khác bản trong file
        jdbc.update("UPDATE question_bank SET question_text = 'old wording' WHERE question_id = ?::uuid", textChanged);
        jdbc.update("UPDATE question_bank SET payload = '{\"min_words\":20,\"max_words\":250}'::jsonb"
                + " WHERE question_id = ?::uuid", payloadChanged);

        runSeed();

        for (String id : List.of(textChanged, payloadChanged)) {
            assertThat(jdbc.queryForObject("""
                    SELECT status || '/' || (embedding_vector IS NULL) || '/' || (embedding_model IS NULL)
                           || '/' || (retired_at IS NULL) FROM question_bank WHERE question_id = ?::uuid
                    """, String.class, id)).isEqualTo("retired/true/true/true");
        }
        assertThat(jdbc.queryForObject("SELECT question_text FROM question_bank WHERE question_id = ?::uuid",
                String.class, textChanged)).isEqualTo(seedText);
        assertThat(jdbc.queryForObject("SELECT payload->>'max_words' FROM question_bank WHERE question_id = ?::uuid",
                String.class, payloadChanged)).isEqualTo(seedMaxWords);
        // Đề không đổi vẫn giữ embedding
        assertThat(jdbc.queryForObject(
                "SELECT status || '/' || (embedding_vector IS NOT NULL) FROM question_bank WHERE question_id = ?::uuid",
                String.class, untouched)).isEqualTo("active/true");
    }

    private void activate(String id) {
        jdbc.update("UPDATE question_bank SET embedding_vector = " + ZERO_VECTOR
                + ", embedding_model = 'test-model', status = 'active' WHERE question_id = ?::uuid", id);
    }

    private String firstId(String level) {
        return jdbc.queryForObject("SELECT question_id::text FROM question_bank WHERE " + SEED_IDS
                + " AND cefr_level = ? ORDER BY question_id LIMIT 1", String.class, level);
    }

    private void runSeed() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(SEED_FILE));
        }
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }
}
