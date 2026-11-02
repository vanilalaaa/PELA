package com.pela.rag.evaluation;

import com.pela.support.PgvectorContainer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Postgres local (Testcontainers {@code pgvector/pgvector:pg17}) để đo độ trễ mà không có độ trễ mạng tới Supabase: chạy stub +
 * migration + seed dev (đúng dữ liệu Supabase dev), rồi nạp embedding của 150 đề từ fixture
 * {@code docs/evaluation/rag/fixtures/question-bank-embeddings.csv} (xuất từ Supabase dev bằng {@code RagFixtureExportTest}).
 * Gemini vẫn được gọi thật.
 */
final class RagLocalDatabase {

    static final Path FIXTURE = RagEvalSupport.EVAL_DIR.resolve("fixtures").resolve("question-bank-embeddings.csv");
    static final String FIXTURE_HEADER = "question_id,embedding_model,embedding";
    private static final String DATABASE = "rag_latency_local";

    private RagLocalDatabase() {
    }

    /** Thông tin kết nối: {url, user, password}. Skip test nếu không có Docker hoặc thiếu fixture. */
    static String[] prepare(Path fixture) throws IOException {
        Assumptions.assumeTrue(org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available: local mode needs Testcontainers");
        Assumptions.assumeTrue(Files.exists(fixture), "Fixture not found: " + fixture
                + " (export it from Supabase dev with RagFixtureExportTest, see docs/evaluation/rag/README.md)");
        String url = PgvectorContainer.jdbcUrlFor(DATABASE);
        String user = PgvectorContainer.instance().getUsername();
        String password = PgvectorContainer.instance().getPassword();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/stub", "filesystem:../db/migrations", "filesystem:../db/seed")
                .load().migrate();
        loadEmbeddings(new JdbcTemplate(dataSource), fixture);
        return new String[] {url, user, password};
    }

    /**
     * Nạp embedding vào các đề seed (cùng question_id) và chuyển sang 'active', như job backfill làm trên Supabase.
     *
     * @return số đề đã được kích hoạt
     */
    static int loadEmbeddings(JdbcTemplate jdbc, Path fixture) throws IOException {
        int activated = 0;
        for (List<String> row : RagEvalSupport.readCsv(fixture, cells -> cells)) {
            activated += jdbc.update("""
                    UPDATE question_bank SET embedding_vector = ?::vector, embedding_model = ?, status = 'active',
                                             retired_at = NULL
                    WHERE question_id = ?::uuid AND question_purpose = 'writing_prompt'
                    """, row.get(2), row.get(1), row.get(0));
        }
        return activated;
    }
}
