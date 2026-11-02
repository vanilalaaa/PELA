package com.pela.rag.evaluation;

import com.pela.platform.adapter.out.gemini.GeminiEmbeddingAdapter;
import com.pela.rag.adapter.out.persistence.JdbcWritingPromptVectorAdapter;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.SettingsPort;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import org.junit.jupiter.api.Assumptions;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/**
 * Kết nối cho đánh giá RAG trên Supabase dev. Không dựng Spring context nên Flyway không chạy. Mọi truy vấn chạy
 * trong transaction READ ONLY và luôn rollback ({@link #readOnly}); SET LOCAL chỉ sống trong transaction đó.
 * Biến DB_URL/DB_USER/DB_PASSWORD/GEMINI_API_KEY đọc từ môi trường hoặc backend/.env, không bao giờ in ra; thiếu thì
 * test bị skip kèm lý do.
 */
final class RagEvalSupport implements AutoCloseable {

    static final Path EVAL_DIR = Path.of("../docs/evaluation/rag");
    static final Path OUT_DIR = Path.of("build", "evaluation", "rag");
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final JdbcWritingPromptVectorAdapter vectors;
    final SettingsPort settings;
    private final SingleConnectionDataSource dataSource;

    private RagEvalSupport(String url, String user, String password) {
        dataSource = new SingleConnectionDataSource(url, user, password, true);
        jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        manager.setEnforceReadOnly(true);
        tx = new TransactionTemplate(manager);
        tx.setReadOnly(true);
        vectors = new JdbcWritingPromptVectorAdapter(jdbc, tx);
        settings = key -> readOnly(() -> jdbc.queryForList(
                "SELECT value::text FROM system_setting WHERE setting_key = ?", String.class, key).stream().findFirst());
    }

    /** Skip test nếu thiếu cấu hình DB. */
    static RagEvalSupport connect() throws IOException {
        String url = config("DB_URL");
        String user = config("DB_USER");
        String password = config("DB_PASSWORD");
        Assumptions.assumeTrue(url != null && user != null && password != null,
                "DB_URL / DB_USER / DB_PASSWORD not set (environment or backend/.env): skipping RAG evaluation");
        return new RagEvalSupport(url, user, password);
    }

    /** Skip test nếu thiếu GEMINI_API_KEY. Model và số chiều đọc từ system_setting của DB. */
    EmbeddingPort embedding() throws IOException {
        String apiKey = config("GEMINI_API_KEY");
        Assumptions.assumeTrue(apiKey != null, "GEMINI_API_KEY not set: skipping RAG evaluation");
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        factory.setReadTimeout(Duration.ofSeconds(60));
        RestClient client = RestClient.builder().baseUrl(GEMINI_BASE_URL).requestFactory(factory)
                .defaultHeader("x-goog-api-key", apiKey).build();
        return new GeminiEmbeddingAdapter(client, settings, Duration.ofSeconds(60));
    }

    /** Chạy trong transaction READ ONLY rồi rollback, kể cả khi thành công. */
    <T> T readOnly(java.util.function.Supplier<T> work) {
        return tx.execute(status -> {
            status.setRollbackOnly();
            return work.get();
        });
    }

    static <T> List<T> readCsv(Path path, Function<List<String>, T> mapper) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<T> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                rows.add(mapper.apply(parseCsvLine(lines.get(i))));
            }
        }
        return rows;
    }

    /** CSV một dòng: ô trong "..." được chứa dấu phẩy, "" là dấu nháy. */
    static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted && c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                cell.append('"');
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    static String csvCell(Object value) {
        String s = String.valueOf(value);
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    static Path write(String fileName, List<String> lines) throws IOException {
        Files.createDirectories(OUT_DIR);
        Path out = OUT_DIR.resolve(fileName);
        Files.write(out, lines, StandardCharsets.UTF_8);
        return out.toAbsolutePath();
    }

    static String config(String name) throws IOException {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            Path env = Path.of(".env");
            if (Files.exists(env)) {
                Properties properties = new Properties();
                try (InputStream in = Files.newInputStream(env)) {
                    properties.load(in);
                }
                value = properties.getProperty(name);
            }
        }
        return Optional.ofNullable(value).map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
    }

    @Override
    public void close() {
        dataSource.destroy();
    }
}
