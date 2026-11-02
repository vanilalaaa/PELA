package com.pela.writing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.platform.adapter.out.gemini.GeminiLlmAdapter;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.PromptTemplatePort;
import com.pela.shared.application.port.out.PromptTemplatePort.PromptTemplate;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.writing.application.usecase.GradingResponse;
import com.pela.writing.domain.TextAnalysis;
import com.pela.writing.domain.TextAnalysis.Sentence;
import com.pela.writing.domain.WritingGrading;
import com.pela.writing.domain.WritingGrading.AiIssue;
import com.pela.writing.domain.WritingGrading.IssueCategory;
import com.pela.writing.domain.WritingGrading.LocatedIssue;
import jakarta.validation.Validation;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * PER-2: đo Precision/Recall theo loại lỗi và tỉ lệ JSON hợp lệ của prompt writing_grading v2 trên bộ câu có chú thích
 * (docs/evaluation/writing-grading). Gọi Gemini thật — chỉ chạy bằng {@code ./gradlew externalTest}. Không in key, không
 * log nội dung câu; kết quả ghi vào build/evaluation.
 */
@Tag("external")
class WritingGradingEvaluationTest {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final Path MIGRATION = Path.of("../db/migrations/V7__writing_grading_prompt_v2.sql");
    private static final List<IssueCategory> REPORTED = List.of(IssueCategory.GRAMMAR, IssueCategory.SPELLING,
            IssueCategory.WORD_CHOICE, IssueCategory.REPETITION, IssueCategory.PUNCTUATION);
    /** PER-2 chỉ kết luận được với bộ ≥ 100 câu. */
    private static final int MIN_SENTENCES_FOR_VERDICT = 100;

    record Expected(IssueCategory category, String original) {
    }

    record Row(String sentence, List<Expected> expected) {
    }

    /** Đếm cho một loại lỗi. */
    static final class Tally {
        int predicted;
        int predictedCorrect;
        int expected;
        int expectedFound;

        double precision() {
            return predicted == 0 ? Double.NaN : (double) predictedCorrect / predicted;
        }

        double recall() {
            return expected == 0 ? Double.NaN : (double) expectedFound / expected;
        }
    }

    @Test
    void evaluateWritingGradingPrompt() throws IOException {
        String apiKey = config("GEMINI_API_KEY", null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set (environment or backend/.env)");
        }
        String level = config("WRITING_EVAL_LEVEL", "B1");
        List<Row> rows = readDataset(Path.of(config("WRITING_EVAL_DATASET", "../docs/evaluation/writing-grading/dataset.csv")));
        GeminiLlmAdapter llm = adapter(apiKey, promptFromMigration());

        Map<IssueCategory, Tally> tallies = new EnumMap<>(IssueCategory.class);
        REPORTED.forEach(c -> tallies.put(c, new Tally()));
        Map<String, Integer> errors = new HashMap<>();
        int valid = 0;
        int invalidJson = 0;
        List<String> details = new ArrayList<>();
        details.add("row,status,expected_issues,predicted_issues");

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            List<Sentence> sentences = TextAnalysis.splitSentences(row.sentence());
            GradingResponse response;
            try {
                response = llm.generateJson("writing_grading", vars(row.sentence(), sentences, level), GradingResponse.class,
                        Duration.ofSeconds(60)).value();
            } catch (AiCallException e) {
                if (e.kind() == AiCallException.Kind.INVALID_JSON) {
                    invalidJson++;
                } else {
                    errors.merge(e.kind().name(), 1, Integer::sum);
                }
                details.add(csv(i + 1, e.kind().name(), format(row.expected()), ""));
                continue;
            }
            valid++;
            List<LocatedIssue> predicted = WritingGrading.locate(row.sentence(), sentences, response.issues().stream()
                    .map(x -> new AiIssue(x.sentenceIndex(), x.original(), x.suggestion(), x.category(), x.severity(),
                            x.explanationVi()))
                    .toList()).stream()
                    .filter(x -> x.category() != IssueCategory.STYLE)
                    .toList();
            score(row, predicted, tallies);
            details.add(csv(i + 1, "ok", format(row.expected()), predicted.stream()
                    .map(p -> p.category().dbValue() + ":" + p.original())
                    .collect(Collectors.joining("|"))));
        }

        int counted = valid + invalidJson;
        double validJsonRate = counted == 0 ? Double.NaN : (double) valid / counted;
        Tally overall = new Tally();
        List<String> results = new ArrayList<>();
        results.add("category,expected,predicted,expected_found,predicted_correct,precision,recall");
        for (IssueCategory c : REPORTED) {
            Tally t = tallies.get(c);
            results.add(tallyRow(c.dbValue(), t));
            overall.expected += t.expected;
            overall.expectedFound += t.expectedFound;
            overall.predicted += t.predicted;
            overall.predictedCorrect += t.predictedCorrect;
        }
        results.add(tallyRow("overall", overall));
        results.add("valid_json_rate,%d,%d,,,%s,".formatted(counted, valid, num(validJsonRate)));
        errors.forEach((kind, n) -> results.add("error_" + kind.toLowerCase(Locale.ROOT) + "," + n + ",,,,,"));

        Path out = Path.of("build", "evaluation");
        Files.createDirectories(out);
        Files.write(out.resolve("writing-grading-results.csv"), results, StandardCharsets.UTF_8);
        Files.write(out.resolve("writing-grading-details.csv"), details, StandardCharsets.UTF_8);
        System.out.println("PER-2 writing grading: " + rows.size() + " rows, valid JSON " + valid + "/" + counted
                + ", other errors " + errors + " -> " + out.resolve("writing-grading-results.csv").toAbsolutePath());
        results.forEach(System.out::println);

        if (rows.size() >= MIN_SENTENCES_FOR_VERDICT) {
            Tally grammar = tallies.get(IssueCategory.GRAMMAR);
            assertThat(validJsonRate).as("valid JSON rate").isGreaterThanOrEqualTo(0.98);
            assertThat(grammar.precision()).as("grammar precision").isGreaterThanOrEqualTo(0.85);
            assertThat(grammar.recall()).as("grammar recall").isGreaterThanOrEqualTo(0.85);
        }
    }

    /** Dự đoán đúng = cùng loại và chồng vị trí với một lần xuất hiện (nguyên từ) của đoạn sai kỳ vọng. */
    private static void score(Row row, List<LocatedIssue> predicted, Map<IssueCategory, Tally> tallies) {
        for (LocatedIssue p : predicted) {
            Tally t = tallies.get(p.category());
            if (t == null) {
                continue;
            }
            t.predicted++;
            if (row.expected().stream().anyMatch(e -> e.category() == p.category() && overlaps(row.sentence(), e, p))) {
                t.predictedCorrect++;
            }
        }
        for (Expected e : row.expected()) {
            Tally t = tallies.get(e.category());
            t.expected++;
            if (predicted.stream().anyMatch(p -> p.category() == e.category() && overlaps(row.sentence(), e, p))) {
                t.expectedFound++;
            }
        }
    }

    private static boolean overlaps(String text, Expected expected, LocatedIssue predicted) {
        Matcher m = occurrences(text, expected.original());
        while (m.find()) {
            if (m.start() < predicted.end() && predicted.start() < m.end()) {
                return true;
            }
        }
        return false;
    }

    /** Các lần xuất hiện của đoạn sai, không phân biệt hoa thường; đầu/cuối là chữ thì phải là nguyên từ ("in" ≠ "playing"). */
    static Matcher occurrences(String text, String original) {
        String o = original.toLowerCase(Locale.ROOT);
        String letter = "[\\p{L}\\p{N}]";
        String regex = (Character.isLetterOrDigit(o.charAt(0)) ? "(?<!" + letter + ")" : "") + Pattern.quote(o)
                + (Character.isLetterOrDigit(o.charAt(o.length() - 1)) ? "(?!" + letter + ")" : "");
        return Pattern.compile(regex).matcher(text.toLowerCase(Locale.ROOT));
    }

    /** Cùng biến như WritingGradingService.promptVariables, với đề tự do. */
    private static Map<String, String> vars(String text, List<Sentence> sentences, String level) {
        var repeated = TextAnalysis.repeatedContentWords(text, 3);
        Map<String, String> vars = new HashMap<>();
        vars.put("level", level);
        vars.put("prompt_text", "(free writing, no prompt)");
        vars.put("scoring_note", "(none)");
        vars.put("min_words", "1");
        vars.put("max_words", "300");
        vars.put("word_count", String.valueOf(TextAnalysis.countWords(text)));
        vars.put("numbered_sentences", sentences.stream()
                .map(s -> "[" + s.index() + "] " + s.text())
                .collect(Collectors.joining("\n")));
        vars.put("repeated_words", repeated.isEmpty() ? "(none)" : repeated.stream()
                .map(r -> r.word() + " (" + r.count() + "x)")
                .collect(Collectors.joining(", ")));
        return vars;
    }

    /** Đọc prompt v2 từ migration V7 (dollar-quote $system$, $body$, $json$), để đánh giá đúng bản sẽ chạy. */
    private static PromptTemplate promptFromMigration() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        String system = between(sql, "$system$");
        String body = between(sql, "$body$");
        String schema = between(sql, "$json$");
        return new PromptTemplate("writing_grading", 2, system, body, schema, 0.2);
    }

    private static String between(String sql, String tag) {
        int start = sql.indexOf(tag);
        int end = sql.indexOf(tag, start + tag.length());
        if (start < 0 || end < 0) {
            throw new IllegalStateException("Tag " + tag + " not found in " + MIGRATION);
        }
        return sql.substring(start + tag.length(), end);
    }

    private static GeminiLlmAdapter adapter(String apiKey, PromptTemplate template) throws IOException {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        factory.setReadTimeout(Duration.ofSeconds(90));
        RestClient client = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory)
                .defaultHeader("x-goog-api-key", apiKey).build();
        ObjectMapper json = new ObjectMapper();
        Map<String, String> raw = Map.of(
                "ai.model.text", json.valueToTree(config("GEMINI_TEXT_MODEL", "gemini-3.8-flash")).toString(),
                "ai.safety.block_threshold", "\"BLOCK_LOW_AND_ABOVE\"");
        SettingsPort settings = key -> Optional.ofNullable(raw.get(key));
        PromptTemplatePort prompts = name -> Optional.of(template);
        return new GeminiLlmAdapter(client, prompts, settings, Validation.buildDefaultValidatorFactory().getValidator(),
                json);
    }

    static List<Row> readDataset(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> cells = parseCsvLine(lines.get(i));
            List<Expected> expected = new ArrayList<>();
            String issues = cells.size() > 1 ? cells.get(1).trim() : "";
            for (String item : issues.isEmpty() ? new String[0] : issues.split("\\|")) {
                int colon = item.indexOf(':');
                IssueCategory category = colon < 0 ? null : IssueCategory.parse(item.substring(0, colon).trim());
                if (category == null || !REPORTED.contains(category)) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": bad expected issue '" + item + "'");
                }
                String original = item.substring(colon + 1);
                if (original.isBlank()) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": empty excerpt in '" + item + "'");
                }
                expected.add(new Expected(category, original));
            }
            rows.add(new Row(cells.get(0), expected));
        }
        return rows;
    }

    /** CSV một dòng: ô trong "..." được chứa dấu phẩy, "" là dấu nháy. */
    private static List<String> parseCsvLine(String line) {
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

    private static String format(List<Expected> expected) {
        return expected.stream().map(e -> e.category().dbValue() + ":" + e.original()).collect(Collectors.joining("|"));
    }

    private static String csv(Object... cells) {
        List<String> out = new ArrayList<>();
        for (Object cell : cells) {
            out.add("\"" + String.valueOf(cell).replace("\"", "\"\"") + "\"");
        }
        return String.join(",", out);
    }

    private static String tallyRow(String name, Tally t) {
        return "%s,%d,%d,%d,%d,%s,%s".formatted(name, t.expected, t.predicted, t.expectedFound, t.predictedCorrect,
                num(t.precision()), num(t.recall()));
    }

    private static String num(double value) {
        return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String config(String name, String fallback) throws IOException {
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
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
