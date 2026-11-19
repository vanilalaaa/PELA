package com.pela.listening.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Đọc file CSV Listening theo mẫu (Admin.QuestionBank.ImportListening): mỗi dòng là một câu thoại
 * ({@code row_type = line}) hoặc một câu hỏi ({@code row_type = question}); các dòng cùng {@code clip_code} tạo thành
 * một bài nghe, giữ thứ tự trong file. Parser chỉ kiểm cấu trúc (cột, row_type, thông tin bài nghe nhất quán); luật
 * BR-37 nằm ở {@link ListeningClipValidator}.
 */
public final class ListeningCsvParser {

    public static final List<String> COLUMNS = List.of("clip_code", "title", "topic", "cefr_level", "row_type",
            "speaker", "voice", "text_en", "text_vi", "question_type", "option_a", "option_b", "option_c", "option_d",
            "answer", "explanation");

    private ListeningCsvParser() {
    }

    /**
     * @param headerValid false khi thiếu/thừa/trùng cột hoặc file rỗng: khi đó không có bài nghe nào
     */
    public record Result(List<ClipSpec> clips, List<ImportIssue> issues, boolean headerValid) {
    }

    public static Result parse(String csv) {
        List<CsvReader.Record> records;
        try {
            records = CsvReader.read(csv);
        } catch (CsvReader.CsvFormatException e) {
            return new Result(List.of(),
                    List.of(new ImportIssue(null, e.lineNumber(), null, "CSV_MALFORMED", e.getMessage())), false);
        }
        if (records.isEmpty()) {
            return new Result(List.of(), List.of(new ImportIssue(null, 1, null, "CSV_EMPTY", "File has no header row")),
                    false);
        }

        CsvReader.Record header = records.getFirst();
        List<ImportIssue> issues = new ArrayList<>();
        Map<String, Integer> index = headerIndex(header, issues);
        if (!issues.isEmpty()) {
            return new Result(List.of(), issues, false);
        }

        Map<String, ClipBuilder> builders = new LinkedHashMap<>();
        for (CsvReader.Record record : records.subList(1, records.size())) {
            int row = record.lineNumber();
            if (record.cells().size() != COLUMNS.size()) {
                issues.add(new ImportIssue(null, row, null, "COLUMN_COUNT_MISMATCH",
                        "Row has " + record.cells().size() + " columns, expected " + COLUMNS.size()));
                continue;
            }
            Map<String, String> cells = new HashMap<>();
            for (String column : COLUMNS) {
                cells.put(column, record.cells().get(index.get(column)).strip());
            }
            if (cells.values().stream().allMatch(String::isEmpty)) {
                continue;
            }
            String code = cells.get("clip_code");
            if (code.isEmpty()) {
                issues.add(new ImportIssue(null, row, "clip_code", "REQUIRED", "clip_code is required"));
                continue;
            }
            ClipBuilder clip = builders.computeIfAbsent(code, c -> new ClipBuilder(c, row, cells));
            int clipIndex = new ArrayList<>(builders.keySet()).indexOf(code);
            clip.checkSameClipInfo(clipIndex, row, cells, issues);

            String rowType = cells.get("row_type").toLowerCase(Locale.ROOT);
            switch (rowType) {
                case "line" -> clip.addLine(clipIndex, row, cells, issues);
                case "question" -> clip.addQuestion(row, cells);
                default -> issues.add(new ImportIssue(clipIndex, row, "row_type", "INVALID_ROW_TYPE",
                        "row_type must be line or question"));
            }
        }
        return new Result(builders.values().stream().map(ClipBuilder::build).toList(), issues, true);
    }

    private static Map<String, Integer> headerIndex(CsvReader.Record header, List<ImportIssue> issues) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.cells().size(); i++) {
            String name = header.cells().get(i).strip().toLowerCase(Locale.ROOT);
            if (!COLUMNS.contains(name)) {
                issues.add(new ImportIssue(null, header.lineNumber(), name, "UNKNOWN_COLUMN",
                        "Unknown column '" + name + "'"));
            } else if (index.putIfAbsent(name, i) != null) {
                issues.add(new ImportIssue(null, header.lineNumber(), name, "DUPLICATE_COLUMN",
                        "Column '" + name + "' appears more than once"));
            }
        }
        for (String column : COLUMNS) {
            if (!index.containsKey(column)) {
                issues.add(new ImportIssue(null, header.lineNumber(), column, "MISSING_COLUMN",
                        "Missing column '" + column + "'"));
            }
        }
        return index;
    }

    private static final class ClipBuilder {
        private final String code;
        private final int firstRow;
        private final String title;
        private final String topic;
        private final String level;
        private final Map<String, String> voices = new LinkedHashMap<>();
        private final List<ClipSpec.Line> lines = new ArrayList<>();
        private final List<ClipSpec.Question> questions = new ArrayList<>();

        ClipBuilder(String code, int firstRow, Map<String, String> cells) {
            this.code = code;
            this.firstRow = firstRow;
            this.title = cells.get("title");
            this.topic = cells.get("topic");
            this.level = cells.get("cefr_level").toUpperCase(Locale.ROOT);
        }

        /** title/topic/cefr_level để trống ở dòng sau thì lấy theo dòng đầu; ghi khác thì báo lỗi. */
        void checkSameClipInfo(int clipIndex, int row, Map<String, String> cells, List<ImportIssue> issues) {
            conflict(clipIndex, row, "title", title, cells.get("title"), issues);
            conflict(clipIndex, row, "topic", topic, cells.get("topic"), issues);
            conflict(clipIndex, row, "cefr_level", level, cells.get("cefr_level").toUpperCase(Locale.ROOT), issues);
        }

        private void conflict(int clipIndex, int row, String column, String expected, String actual,
                List<ImportIssue> issues) {
            if (!actual.isEmpty() && !actual.equals(expected)) {
                issues.add(new ImportIssue(clipIndex, row, column, "CLIP_INFO_MISMATCH",
                        column + " differs from the first row of clip " + code));
            }
        }

        void addLine(int clipIndex, int row, Map<String, String> cells, List<ImportIssue> issues) {
            String speaker = cells.get("speaker");
            String voice = cells.get("voice");
            if (!speaker.isEmpty() && !voice.isEmpty()) {
                String previous = voices.putIfAbsent(speaker, voice);
                if (previous != null && !Objects.equals(previous, voice)) {
                    issues.add(new ImportIssue(clipIndex, row, "voice", "VOICE_MISMATCH",
                            "Speaker " + speaker + " already uses voice " + previous));
                }
            }
            lines.add(new ClipSpec.Line(row, speaker, cells.get("text_en"), cells.get("text_vi")));
        }

        void addQuestion(int row, Map<String, String> cells) {
            questions.add(new ClipSpec.Question(row, cells.get("question_type").toLowerCase(Locale.ROOT),
                    cells.get("text_en"),
                    List.of(cells.get("option_a"), cells.get("option_b"), cells.get("option_c"), cells.get("option_d")),
                    cells.get("answer").toUpperCase(Locale.ROOT), cells.get("explanation")));
        }

        ClipSpec build() {
            return new ClipSpec(code, firstRow, title, topic, level, voices, lines, questions);
        }
    }
}
