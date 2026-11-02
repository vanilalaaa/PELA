package com.pela.writing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.writing.domain.WritingGrading.AiIssue;
import com.pela.writing.domain.WritingGrading.CriterionScores;
import com.pela.writing.domain.WritingGrading.IssueCategory;
import com.pela.writing.domain.WritingGrading.LocatedIssue;
import com.pela.writing.domain.WritingGrading.Severity;
import com.pela.writing.domain.WritingGrading.StarBand;
import java.util.List;
import org.junit.jupiter.api.Test;

class WritingGradingTest {

    private static final String TEXT = "I goes to school every day. My school is big. I like my school very much.";

    private static List<LocatedIssue> locate(AiIssue... issues) {
        return WritingGrading.locate(TEXT, TextAnalysis.splitSentences(TEXT), List.of(issues));
    }

    private static AiIssue issue(int sentence, String original, String category, String severity) {
        return new AiIssue(sentence, original, "fix", category, severity, "giải thích");
    }

    @Test
    void locatesIssueInsideItsSentence() {
        List<LocatedIssue> located = locate(issue(0, "goes", "grammar", "error"));

        assertThat(located).hasSize(1);
        LocatedIssue i = located.get(0);
        assertThat(TEXT.substring(i.start(), i.end())).isEqualTo("goes");
        assertThat(i.sentenceIndex()).isZero();
        assertThat(i.category()).isEqualTo(IssueCategory.GRAMMAR);
        assertThat(i.severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void usesSentenceToDisambiguateRepeatedText() {
        // "school" xuất hiện ở cả 3 câu; phải lấy đúng vị trí trong câu 2
        LocatedIssue i = locate(issue(2, "school", "repetition", "suggestion")).get(0);

        assertThat(i.start()).isEqualTo(TEXT.lastIndexOf("school"));
        assertThat(i.sentenceIndex()).isEqualTo(2);
    }

    @Test
    void matchesCaseInsensitivelyAndReturnsOriginalText() {
        LocatedIssue i = locate(issue(1, "MY SCHOOL", "grammar", "error")).get(0);

        assertThat(i.original()).isEqualTo("My school");
    }

    @Test
    void fallsBackToUniqueOccurrenceWhenSentenceIndexIsWrong() {
        LocatedIssue i = locate(issue(2, "goes", "grammar", "error")).get(0);

        assertThat(i.sentenceIndex()).isZero();
        assertThat(TEXT.substring(i.start(), i.end())).isEqualTo("goes");
    }

    @Test
    void dropsUnlocatableAmbiguousOrUnknownIssues() {
        assertThat(locate(
                issue(0, "went", "grammar", "error"),          // không có trong bài
                issue(5, "school", "grammar", "error"),        // câu sai và xuất hiện nhiều lần
                issue(0, "goes", "tense", "error"),            // danh mục lạ
                issue(0, " ", "grammar", "error"))).isEmpty();
    }

    @Test
    void styleIsAlwaysSuggestion() {
        LocatedIssue i = locate(issue(2, "very much", "style", "error")).get(0);

        assertThat(i.severity()).isEqualTo(Severity.SUGGESTION);
    }

    @Test
    void dropsDuplicateSpanAndCategory() {
        assertThat(locate(issue(0, "goes", "grammar", "error"), issue(0, "goes", "grammar", "error"))).hasSize(1);
        assertThat(locate(issue(0, "goes", "grammar", "error"), issue(0, "goes", "spelling", "error"))).hasSize(2);
    }

    @Test
    void totalIsSumOfClampedCriteria() {
        assertThat(new CriterionScores(20, 18, 15, 22).total()).isEqualTo(75);
        assertThat(new CriterionScores(30, -5, 25, 25).total()).isEqualTo(75);
    }

    @Test
    void starsFromHighestReachedBand() {
        List<StarBand> bands = List.of(new StarBand(90, 5), new StarBand(75, 4), new StarBand(60, 3),
                new StarBand(40, 2), new StarBand(20, 1));

        assertThat(WritingGrading.stars(100, bands)).isEqualTo(5);
        assertThat(WritingGrading.stars(90, bands)).isEqualTo(5);
        assertThat(WritingGrading.stars(89, bands)).isEqualTo(4);
        assertThat(WritingGrading.stars(60, bands)).isEqualTo(3);
        assertThat(WritingGrading.stars(20, bands)).isEqualTo(1);
        assertThat(WritingGrading.stars(19, bands)).isZero();
        assertThat(WritingGrading.stars(50, List.of())).isZero();
    }
}
