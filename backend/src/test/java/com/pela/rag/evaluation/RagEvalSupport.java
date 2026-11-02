package com.pela.rag.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.platform.adapter.out.contentfilter.BlocklistContentFilterAdapter;
import com.pela.platform.adapter.out.gemini.GeminiEmbeddingAdapter;
import com.pela.platform.adapter.out.gemini.GeminiLlmAdapter;
import com.pela.platform.adapter.out.persistence.JdbcPromptTemplateAdapter;
import com.pela.rag.adapter.out.persistence.JdbcGenerationLogAdapter;
import com.pela.rag.adapter.out.persistence.JdbcPromptCacheAdapter;
import com.pela.rag.adapter.out.persistence.JdbcPromptExampleDetailsAdapter;
import com.pela.rag.adapter.out.persistence.JdbcWritingPromptVectorAdapter;
import com.pela.rag.application.port.out.LearnerContextPort.LearnerContext;
import com.pela.rag.application.usecase.GenerateWritingPromptService;
import com.pela.rag.domain.RagQueryBuilder;
import com.pela.shared.adapter.out.SystemClockAdapter;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.writing.adapter.out.persistence.JdbcCuratedPromptAdapter;
import com.pela.writing.application.usecase.PickCuratedPromptService;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Assumptions;
import jakarta.validation.Validation;
import org.springframework.core.io.DefaultResourceLoader;
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


    /**
     * Một dòng queries.csv. Truy vấn thật dựng bằng {@link RagQueryBuilder} (RAG.Embed.Input) từ {@code topic} + level + sở
     * thích, giống hệt UC-24; truy hồi lọc theo {@code filterTopic} + level.
     *
     * @param interests   cột {@code interests}, các sở thích ngăn bằng "|"; rỗng = learner không có sở thích
     * @param filterTopic topic dùng để lọc truy hồi; cột trống = {@code topic}. Khác {@code topic} = mô phỏng kho không có
     *                    đề sát yêu cầu (nhãn off_topic)
     * @param note        {@code kho_gap}: kho chưa có đề gần chủ đề này, loại khỏi phần tính ngưỡng; {@code interest_far}:
     *                    sở thích xa topic nhưng vẫn là yêu cầu bình thường (on_topic), thống kê riêng
     */
    record QueryRow(String topic, String level, List<String> interests, String filterTopic, boolean onTopic,
            String note) {

        String text() {
            return RagQueryBuilder.build(topic, level, interests);
        }

        boolean khoGap() {
            return "kho_gap".equals(note);
        }

        boolean interestFar() {
            return "interest_far".equals(note);
        }
    }

    /** Đọc docs/evaluation/rag/queries.csv (cột topic,level,interests,filter_topic,expected,note). */
    static List<QueryRow> readQueries() throws IOException {
        return readCsv(EVAL_DIR.resolve("queries.csv"), c -> new QueryRow(c.get(0).trim(), c.get(1).trim(),
                c.get(2).isBlank() ? List.of() : List.of(c.get(2).trim().split("\\|")),
                c.get(3).isBlank() ? c.get(0).trim() : c.get(3).trim(), c.get(4).trim().equals("on_topic"),
                c.size() > 5 ? c.get(5).trim() : ""));
    }

    /** Dạng văn bản của pgvector: [x1,x2,...]. */
    static String vectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            sb.append(i == 0 ? "" : ",").append(vector[i]);
        }
        return sb.append(']').toString();
    }

    /**
     * UC-24 nối với adapter thật (DB Supabase dev, Gemini). Mọi thứ chạy trong MỘT transaction ghi rồi ROLLBACK
     * ({@link Pipeline#inRolledBackTransaction}): cache, lần phát và nhật ký không để lại gì trong DB. Dùng learner seed
     * (R__dev_seed.sql) làm người nhận; thiếu thì skip. Chỉ giới hạn "đề AI mỗi ngày" bị nâng lên để đo liên tục.
     */
    Pipeline pipeline() throws IOException {
        UUID user = UUID.fromString(SEED_USER);
        Assumptions.assumeTrue(Boolean.TRUE.equals(readOnly(() -> jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE user_id = ?)", Boolean.class, user))),
                "Seed learner " + SEED_USER + " not found: run the dev profile (R__dev_seed.sql) first");
        String apiKey = config("GEMINI_API_KEY");
        Assumptions.assumeTrue(apiKey != null, "GEMINI_API_KEY not set: skipping RAG evaluation");

        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate writable = new TransactionTemplate(manager);
        SystemClockAdapter clock = new SystemClockAdapter();
        SettingsPort evalSettings = key -> key.equals("writing.ai_prompt_daily_limit") ? Optional.of("1000000")
                : settings.rawJson(key);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        factory.setReadTimeout(Duration.ofSeconds(90));
        RestClient client = RestClient.builder().baseUrl(GEMINI_BASE_URL).requestFactory(factory)
                .defaultHeader("x-goog-api-key", apiKey).build();
        GeminiLlmAdapter llm = new GeminiLlmAdapter(client, new JdbcPromptTemplateAdapter(jdbc, clock), evalSettings,
                Validation.buildDefaultValidatorFactory().getValidator(), new ObjectMapper());
        AtomicReference<List<String>> interests = new AtomicReference<>(List.of());
        EmbeddingPort embedding;
        try {
            embedding = embedding();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        JdbcPromptCacheAdapter cache = new JdbcPromptCacheAdapter(jdbc, writable);
        GenerateWritingPromptService generate = new GenerateWritingPromptService(
                id -> new LearnerContext(interests.get(), ZoneId.of("Asia/Ho_Chi_Minh")), cache,
                new JdbcWritingPromptVectorAdapter(jdbc, writable), new JdbcPromptExampleDetailsAdapter(jdbc),
                new JdbcGenerationLogAdapter(jdbc), embedding, llm,
                new BlocklistContentFilterAdapter(new DefaultResourceLoader(), List.of(
                        "classpath:content-filter/blocklist-en.txt", "classpath:content-filter/blocklist-vi.txt")),
                evalSettings, clock, UUID::randomUUID);
        return new Pipeline(user, writable, interests, generate, new PickCuratedPromptService(new JdbcCuratedPromptAdapter(jdbc)),
                cache, embedding, settings);
    }

    static final String SEED_USER = "ad000000-0000-4000-8000-000000000001";

    /**
     * @param interests sở thích của learner giả lập; đặt trước mỗi lần gọi
     * @param cache     adapter cache thật để đo nhánh cache hit và dựng entry
     */
    record Pipeline(UUID userId, TransactionTemplate writable, AtomicReference<List<String>> interests,
            GenerateWritingPromptService generate, PickCuratedPromptService curated, JdbcPromptCacheAdapter cache,
            EmbeddingPort embedding, SettingsPort settings) {

        <T> T inRolledBackTransaction(java.util.function.Supplier<T> work) {
            return writable.execute(status -> {
                status.setRollbackOnly();
                return work.get();
            });
        }
    }

    @Override
    public void close() {
        dataSource.destroy();
    }
}
