package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Xuất embedding của các đề Writing đang active từ Supabase dev ra
 * {@code docs/evaluation/rag/fixtures/question-bank-embeddings.csv}, để {@code RAG_LATENCY_MODE=local} nạp vào Postgres local.
 * CHỈ ĐỌC (transaction READ ONLY, không gọi Gemini). Chạy một lần khi cần cập nhật fixture; xem README.
 */
@Tag("external")
class RagFixtureExportTest {

    @Test
    void exportActivePromptEmbeddingsToTheFixture() throws Exception {
        try (RagEvalSupport support = RagEvalSupport.connect()) {
            List<String> lines = new ArrayList<>();
            lines.add(RagLocalDatabase.FIXTURE_HEADER);
            support.readOnly(() -> {
                support.jdbc.query("""
                    SELECT question_id::text AS id, embedding_model, embedding_vector::text AS embedding
                    FROM question_bank
                    WHERE question_purpose = 'writing_prompt' AND status = 'active' AND embedding_vector IS NOT NULL
                    ORDER BY question_id
                    """, rs -> {
                    lines.add(String.join(",", rs.getString("id"), rs.getString("embedding_model"),
                            RagEvalSupport.csvCell(rs.getString("embedding"))));
                });
                return null;
            });

            assertThat(lines.size() - 1).as("active prompts with an embedding").isGreaterThan(0);
            Files.createDirectories(RagLocalDatabase.FIXTURE.getParent());
            Files.write(RagLocalDatabase.FIXTURE, lines, StandardCharsets.UTF_8);
            System.out.println("Exported " + (lines.size() - 1) + " prompt embeddings -> "
                    + RagLocalDatabase.FIXTURE.toAbsolutePath().normalize());
        }
    }
}
