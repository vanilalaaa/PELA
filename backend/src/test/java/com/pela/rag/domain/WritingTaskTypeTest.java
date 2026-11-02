package com.pela.rag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class WritingTaskTypeTest {

    @Test
    void parsesIgnoringCaseAndWhitespace() {
        assertThat(WritingTaskType.parse(" Email ")).contains(WritingTaskType.EMAIL);
        assertThat(WritingTaskType.parse("paragraph")).contains(WritingTaskType.PARAGRAPH);
        assertThat(WritingTaskType.parse("ESSAY")).contains(WritingTaskType.ESSAY);
    }

    @Test
    void unknownOrMissingIsEmpty() {
        assertThat(WritingTaskType.parse("poem")).isEqualTo(Optional.empty());
        assertThat(WritingTaskType.parse(null)).isEmpty();
        assertThat(WritingTaskType.EMAIL.dbValue()).isEqualTo("email");
    }
}
