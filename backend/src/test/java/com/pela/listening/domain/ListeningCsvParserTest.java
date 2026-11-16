package com.pela.listening.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.listening.domain.ListeningClipValidator.Limits;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Admin.QuestionBank.ImportListening + BR-37: đọc CSV theo mẫu và kiểm luật, lỗi theo dòng + cột. */
class ListeningCsvParserTest {

    private static final String HEADER = String.join(",", ListeningCsvParser.COLUMNS);
    private static final Limits LIMITS = new Limits(2, 3000);

    @Test
    void templateFileIsValidAndGroupsRowsByClipInFileOrder() throws IOException {
        ListeningCsvParser.Result result = ListeningCsvParser.parse(template());

        assertThat(result.headerValid()).isTrue();
        assertThat(result.issues()).isEmpty();
        assertThat(result.clips()).extracting(ClipSpec::clipCode).containsExactly("DL-A1-01", "DL-A1-02");
        ClipSpec bakery = result.clips().getFirst();
        assertThat(bakery.row()).isEqualTo(2);
        assertThat(bakery.title()).isEqualTo("At the bakery");
        assertThat(bakery.cefrLevel()).isEqualTo("A1");
        assertThat(bakery.voices()).containsEntry("Linh", "Kore").containsEntry("Baker", "Puck");
        assertThat(bakery.lines()).extracting(ClipSpec.Line::speaker).containsExactly("Linh", "Baker", "Linh");
        // Ô bọc nháy giữ nguyên dấu phẩy và dấu nháy kép
        assertThat(bakery.lines().getFirst().en()).isEqualTo("Good morning! Can I have two croissants, please?");
        assertThat(bakery.questions()).hasSize(2);
        assertThat(bakery.questions().get(0).explanation()).isEqualTo("Linh says \"two croissants, please\".");
        assertThat(bakery.questions().get(1).text()).isEqualTo("Choose the word you hear: \"That's ___ dollars.\"");
        assertThat(bakery.questions().get(1).answer()).isEqualTo("C");
        assertThat(bakery.questions().get(1).row()).isEqualTo(6);
        for (int i = 0; i < result.clips().size(); i++) {
            assertThat(ListeningClipValidator.validate(i, result.clips().get(i), LIMITS)).isEmpty();
        }
    }

    @Test
    void docsTemplateMatchesTheServedTemplate() throws IOException {
        Path docs = Path.of("..", "docs", "template", "listening-import-template.csv");
        assertThat(Files.readString(docs, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .isEqualTo(template().replace("\r\n", "\n"));
    }

    @Test
    void missingColumnRejectsTheWholeFile() {
        String header = HEADER.replace(",explanation", "");
        ListeningCsvParser.Result result = ListeningCsvParser.parse(header + "\nC1,T,Topic,A1,line,Linh,Kore,Hi,Chào,,,,,,\n");

        assertThat(result.headerValid()).isFalse();
        assertThat(result.clips()).isEmpty();
        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("MISSING_COLUMN");
            assertThat(i.column()).isEqualTo("explanation");
            assertThat(i.row()).isEqualTo(1);
        });
    }

    @Test
    void unknownAndDuplicateColumnsAreReported() {
        ListeningCsvParser.Result result = ListeningCsvParser.parse(HEADER + ",extra,title\n");

        assertThat(result.headerValid()).isFalse();
        assertThat(result.issues()).extracting(ImportIssue::code).containsExactly("UNKNOWN_COLUMN", "DUPLICATE_COLUMN");
    }

    @Test
    void threeSpeakersAreRejectedAtTheRowOfTheThirdSpeaker() {
        List<ImportIssue> issues = validate(csv(
                line("Linh", "Kore", "Hello."),
                line("Nam", "Puck", "Hi."),
                line("Mai", "Leda", "Hey."),
                question("dialogue_question", "A", "Because.")));

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("TOO_MANY_SPEAKERS");
            assertThat(i.row()).isEqualTo(4);
            assertThat(i.column()).isEqualTo("speaker");
        });
    }

    @Test
    void answerOutsideAtoDIsRejected() {
        List<ImportIssue> issues = validate(csv(line("Linh", "Kore", "Hello."), question("choose_heard", "E", "Why.")));

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("INVALID_ANSWER");
            assertThat(i.row()).isEqualTo(3);
            assertThat(i.column()).isEqualTo("answer");
        });
    }

    @Test
    void missingExplanationIsRejected() {
        List<ImportIssue> issues = validate(csv(line("Linh", "Kore", "Hello."), question("fill_blank", "B", "")));

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("REQUIRED");
            assertThat(i.column()).isEqualTo("explanation");
            assertThat(i.row()).isEqualTo(3);
        });
    }

    @Test
    void transcriptOverTheCharacterLimitIsRejected() {
        List<ImportIssue> issues = ListeningClipValidator.validate(0,
                ListeningCsvParser.parse(csv(line("Linh", "Kore", "x".repeat(2000)), line("Linh", "Kore", "y".repeat(1001)),
                        question("choose_heard", "A", "Because."))).clips().getFirst(), LIMITS);

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("TRANSCRIPT_TOO_LONG");
            assertThat(i.column()).isEqualTo("text_en");
            assertThat(i.row()).isEqualTo(2);
        });
    }

    @Test
    void duplicateOptionsInvalidTypeAndLevelAreRejected() {
        String row = "C1,Title,Topic,Z9,question,,,What?,Gì?,multiple,One,Two,one,Four,A,Because.";
        List<ImportIssue> issues = validate(HEADER + "\n" + "C1,Title,Topic,Z9,line,Linh,Kore,Hi.,Chào.,,,,,,,\n" + row + "\n");

        assertThat(issues).extracting(ImportIssue::code)
                .containsExactlyInAnyOrder("INVALID_CEFR_LEVEL", "INVALID_QUESTION_TYPE", "DUPLICATE_OPTION");
        assertThat(issues).filteredOn(i -> i.code().equals("DUPLICATE_OPTION")).singleElement()
                .satisfies(i -> assertThat(i.column()).isEqualTo("option_c"));
    }

    @Test
    void clipWithoutQuestionsOrLinesIsRejected() {
        List<ImportIssue> issues = validate(csv(line("Linh", "Kore", "Hello.")));

        assertThat(issues).extracting(ImportIssue::code).containsExactly("NO_QUESTIONS");
    }

    @Test
    void commasQuotesAndLineBreaksInsideQuotedCellsAreKept() {
        String row = "C1,\"Title, with comma\",Topic,A1,line,Linh,Kore,\"She said \"\"hi, there\"\"\nand left.\",\"Cô ấy nói \"\"chào\"\"\",,,,,,,";
        String csv = HEADER + "\r\n" + row + "\r\n" + question("choose_heard", "A", "Because.").replace("ROW", "C1");
        ListeningCsvParser.Result result = ListeningCsvParser.parse(csv);

        assertThat(result.issues()).isEmpty();
        ClipSpec clip = result.clips().getFirst();
        assertThat(clip.title()).isEqualTo("Title, with comma");
        assertThat(clip.lines().getFirst().en()).isEqualTo("She said \"hi, there\"\nand left.");
        assertThat(clip.lines().getFirst().vi()).isEqualTo("Cô ấy nói \"chào\"");
        // Câu hỏi bắt đầu ở dòng vật lý 4 vì ô trước có xuống dòng
        assertThat(clip.questions().getFirst().row()).isEqualTo(4);
    }

    @Test
    void wrongCellCountAndInvalidRowTypeAreReportedByRow() {
        String csv = HEADER + "\nC1,T,Topic,A1,line,Linh\nC1,T,Topic,A1,note,,,,,,,,,,,\n";
        ListeningCsvParser.Result result = ListeningCsvParser.parse(csv);

        assertThat(result.issues()).extracting(ImportIssue::code, ImportIssue::row)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("COLUMN_COUNT_MISMATCH", 2),
                        org.assertj.core.groups.Tuple.tuple("INVALID_ROW_TYPE", 3));
    }

    @Test
    void conflictingVoiceForTheSameSpeakerIsReported() {
        ListeningCsvParser.Result result = ListeningCsvParser.parse(csv(line("Linh", "Kore", "Hi."),
                line("Linh", "Puck", "Bye."), question("choose_heard", "A", "Because.")));

        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("VOICE_MISMATCH");
            assertThat(i.row()).isEqualTo(3);
            assertThat(i.column()).isEqualTo("voice");
        });
    }

    @Test
    void unterminatedQuoteIsMalformed() {
        ListeningCsvParser.Result result = ListeningCsvParser.parse(HEADER + "\nC1,\"open,,,\n");

        assertThat(result.headerValid()).isFalse();
        assertThat(result.issues()).extracting(ImportIssue::code).containsExactly("CSV_MALFORMED");
    }

    private static List<ImportIssue> validate(String csv) {
        ListeningCsvParser.Result result = ListeningCsvParser.parse(csv);
        List<ImportIssue> issues = new ArrayList<>(result.issues());
        for (int i = 0; i < result.clips().size(); i++) {
            issues.addAll(ListeningClipValidator.validate(i, result.clips().get(i), LIMITS));
        }
        return issues;
    }

    private static String csv(String... rows) {
        return HEADER + "\n" + String.join("\n", rows).replace("ROW", "C1") + "\n";
    }

    private static String line(String speaker, String voice, String en) {
        return "ROW,Title,Topic,A1,line," + speaker + "," + voice + "," + en + ",Bản dịch,,,,,,,";
    }

    private static String question(String type, String answer, String explanation) {
        return "ROW,,,,question,,,What did you hear?,Bạn nghe gì?," + type + ",one,two,three,four," + answer + ","
                + explanation;
    }

    private static String template() throws IOException {
        try (InputStream in = ListeningCsvParserTest.class.getResourceAsStream("/templates/listening-import-template.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
