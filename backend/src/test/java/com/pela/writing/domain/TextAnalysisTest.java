package com.pela.writing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.writing.domain.TextAnalysis.RepeatedWord;
import com.pela.writing.domain.TextAnalysis.Sentence;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextAnalysisTest {

    /** Test vector dùng chung với frontend/packages/domain (wordCount.test.ts). */
    static Stream<Arguments> wordCountCases() throws IOException {
        JsonNode root = new ObjectMapper().readTree(Path.of("../frontend/packages/fixtures/word-count-cases.json").toFile());
        return StreamSupport.stream(root.get("cases").spliterator(), false)
                .map(c -> Arguments.of(c.get("id").asText(), c.get("text").asText(), c.get("expected").asInt()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wordCountCases")
    void countsWordsLikeFrontend(String id, String text, int expected) {
        assertThat(TextAnalysis.countWords(text)).isEqualTo(expected);
    }

    @Test
    void countsNullAsZero() {
        assertThat(TextAnalysis.countWords(null)).isZero();
    }

    @Test
    void splitsSentencesWithOriginalOffsets() {
        String text = "I go to school. It is big! Do you like it?";

        List<Sentence> sentences = TextAnalysis.splitSentences(text);

        assertThat(sentences).extracting(Sentence::text)
                .containsExactly("I go to school.", "It is big!", "Do you like it?");
        for (Sentence s : sentences) {
            assertThat(text.substring(s.start(), s.end())).isEqualTo(s.text());
        }
        assertThat(sentences).extracting(Sentence::index).containsExactly(0, 1, 2);
    }

    @Test
    void doesNotSplitOnAbbreviationsOrDecimals() {
        List<Sentence> sentences = TextAnalysis.splitSentences("Mr. Smith paid 2.5 dollars at 9 a.m. today. Then he left.");

        assertThat(sentences).extracting(Sentence::text)
                .containsExactly("Mr. Smith paid 2.5 dollars at 9 a.m. today.", "Then he left.");
    }

    @Test
    void splitsOnNewlinesAndKeepsClosingQuotes() {
        List<Sentence> sentences = TextAnalysis.splitSentences("He said \"Hi.\" Then left\n\nNew line here");

        assertThat(sentences).extracting(Sentence::text)
                .containsExactly("He said \"Hi.\"", "Then left", "New line here");
    }

    @Test
    void emptyTextHasNoSentences() {
        assertThat(TextAnalysis.splitSentences("  ")).isEmpty();
    }

    @Test
    void findsRepeatedContentWordsOnly() {
        String text = "The movie was good. The music was good. The food was good and the movie was long. Movie!";

        List<RepeatedWord> repeated = TextAnalysis.repeatedContentWords(text, 3);

        // "the", "was", "and" là từ chức năng; "movie" không phân biệt hoa thường
        assertThat(repeated).containsExactly(new RepeatedWord("good", 3), new RepeatedWord("movie", 3));
    }

    @Test
    void ignoresShortWordsAndNumbers() {
        assertThat(TextAnalysis.repeatedContentWords("go go go 100 100 100 ox ox ox", 3)).isEmpty();
    }
}
