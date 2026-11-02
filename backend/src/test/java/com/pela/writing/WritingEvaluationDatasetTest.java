package com.pela.writing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Kiểm tra bộ dữ liệu PER-2 (docs/evaluation/writing-grading/dataset.csv) ngay trong build, không gọi AI. */
class WritingEvaluationDatasetTest {

    @Test
    void everyExpectedExcerptOccursInItsSentence() throws IOException {
        var rows = WritingGradingEvaluationTest.readDataset(Path.of("../docs/evaluation/writing-grading/dataset.csv"));

        assertThat(rows).isNotEmpty();
        for (var row : rows) {
            assertThat(row.sentence()).isNotBlank();
            for (var expected : row.expected()) {
                assertThat(WritingGradingEvaluationTest.occurrences(row.sentence(), expected.original()).find())
                        .as("'%s' in \"%s\"", expected.original(), row.sentence())
                        .isTrue();
            }
        }
    }
}
