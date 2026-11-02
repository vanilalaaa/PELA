package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Chế độ local của bài đo độ trễ: migrate + seed rồi nạp embedding từ fixture lên các đề seed (không gọi Gemini). */
@Testcontainers(disabledWithoutDocker = true)
class RagLocalFixtureTest {

    /** 3 đề trong bộ 138 đề (R__dev_seed_writing_prompts.sql). */
    private static final List<String> SEED_IDS = List.of("9b000000-0000-4000-8000-001001000000",
            "9b000000-0000-4000-8000-001002000000", "9b000000-0000-4000-8000-002001000000");

    @TempDir
    Path dir;

    @Test
    void loadsFixtureEmbeddingsIntoTheSeededPromptsAndActivatesThem() throws Exception {
        Path fixture = dir.resolve("fixture.csv");
        List<String> lines = new ArrayList<>(List.of(RagLocalDatabase.FIXTURE_HEADER));
        Random random = new Random(3);
        for (String id : SEED_IDS) {
            lines.add(id + ",gemini-embedding-001," + RagEvalSupport.csvCell(vector(random)));
        }
        lines.add(UUID.randomUUID() + ",gemini-embedding-001," + RagEvalSupport.csvCell(vector(random))); // không có trong kho
        Files.write(fixture, lines, StandardCharsets.UTF_8);

        String[] connection = RagLocalDatabase.prepare(fixture);

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(connection[0], connection[1], connection[2]));
        assertThat(jdbc.queryForList("""
                SELECT question_id::text FROM question_bank
                WHERE question_purpose = 'writing_prompt' AND status = 'active' AND embedding_vector IS NOT NULL
                  AND question_id::text LIKE '9b000000-%' ORDER BY question_id
                """, String.class)).containsExactlyElementsOf(SEED_IDS);
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding_vector) FROM question_bank WHERE question_id = ?::uuid",
                Integer.class, SEED_IDS.get(0))).isEqualTo(768);
        // Các đề seed không có trong fixture vẫn ở trạng thái chưa kích hoạt
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM question_bank WHERE question_id::text LIKE '9b000000-%' AND status = 'retired'
                """, Integer.class)).isEqualTo(135);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE user_id = ?::uuid", Integer.class,
                RagEvalSupport.SEED_USER)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT value::text FROM system_setting WHERE setting_key = 'ai.model.embedding'",
                String.class)).isEqualTo("\"gemini-embedding-001\"");
    }

    private static String vector(Random random) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 768; i++) {
            sb.append(i == 0 ? "" : ",").append(random.nextFloat() - 0.5f);
        }
        return sb.append(']').toString();
    }
}
