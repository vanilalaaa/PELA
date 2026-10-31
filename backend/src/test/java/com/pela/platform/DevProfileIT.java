package com.pela.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pela.support.PgvectorContainer;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chạy với application-dev.yml thật, trên database riêng (seed dev không lẫn vào DB của test khác):
 * migrate sạch stub → V1 → V3 rồi seed dev. Chỉ thêm stub schema auth vào locations.
 */
@ActiveProfiles("dev")
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/stub,filesystem:../db/migrations,filesystem:../db/seed")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DevProfileIT {

    private static final String TRAVEL = "70000000-0000-4000-8000-000000000001";
    private static final String WORK = "70000000-0000-4000-8000-000000000002";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PgvectorContainer.jdbcUrlFor("dev_profile_check"));
        registry.add("spring.datasource.username", () -> PgvectorContainer.instance().getUsername());
        registry.add("spring.datasource.password", () -> PgvectorContainer.instance().getPassword());
    }

    @Autowired
    Flyway flyway;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    TransactionTemplate tx;

    /** Seed dev là migration lặp lại: không trùng version với migration thật và chạy được sau V3. */
    @Test
    void devSeedAppliesAfterMigrations() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().all())
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
        assertThat(flyway.info().all())
                .extracting(MigrationInfo::getScript)
                .contains("V3__ai_prompt_and_listening_tts.sql", "R__dev_seed.sql");
    }

    @Test
    void devSeedHasMinimalLearningContent() {
        assertThat(count("SELECT count(*) FROM survey_question")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM survey_level_rule WHERE answer_pattern = '{}'")).isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT cefr_level || ' ' || name FROM topic ORDER BY cefr_level", String.class))
                .containsExactly("A2 Travel", "B1 Work");
        for (String topic : List.of(TRAVEL, WORK)) {
            assertThat(count("SELECT count(*) FROM lesson WHERE topic_id = '" + topic + "'")).isEqualTo(2);
            assertThat(count("""
                    SELECT count(DISTINCT lv.vocab_id) FROM lesson_vocab lv
                    JOIN lesson l USING (lesson_id) WHERE l.topic_id = '%s'
                    """.formatted(topic))).isEqualTo(20);
        }

        assertThat(count("SELECT count(*) FROM listening_clip WHERE audio_url = 'pending://'")).isEqualTo(2);
    }

    @Test
    void devSeedIsIdempotent() {
        String countsBefore = seedCounts();

        // Chạy lại nguyên file trong một transaction như Flyway (temp table ON COMMIT DROP)
        tx.executeWithoutResult(status -> ScriptUtils.executeSqlScript(
                DataSourceUtils.getConnection(dataSource), new FileSystemResource("../db/seed/R__dev_seed.sql")));

        assertThat(seedCounts()).isEqualTo(countsBefore);
    }

    @Test
    void apiDocsArePublicInDev() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/v1/health']").exists());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    private String seedCounts() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM survey_question) || '/' || (SELECT count(*) FROM survey_level_rule)
                    || '/' || (SELECT count(*) FROM topic) || '/' || (SELECT count(*) FROM lesson)
                    || '/' || (SELECT count(*) FROM vocabulary) || '/' || (SELECT count(*) FROM lesson_vocab)
                    || '/' || (SELECT count(*) FROM listening_clip)
                """, String.class);
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }
}
