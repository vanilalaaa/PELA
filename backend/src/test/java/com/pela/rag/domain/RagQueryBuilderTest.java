package com.pela.rag.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagQueryBuilderTest {

    @Test
    void buildsQueryFromTopicLevelAndInterests() {
        assertThat(RagQueryBuilder.build("Travel", "A2", List.of("food", "music")))
                .isEqualTo("Writing prompt. Topic: Travel. CEFR level: A2. Learner interests: food, music.");
    }

    @Test
    void omitsInterestsSentenceWhenThereAreNone() {
        String expected = "Writing prompt. Topic: Work. CEFR level: B1.";

        assertThat(RagQueryBuilder.build("Work", "B1", List.of())).isEqualTo(expected);
        assertThat(RagQueryBuilder.build("Work", "B1", null)).isEqualTo(expected);
        assertThat(RagQueryBuilder.build("Work", "B1", Arrays.asList(" ", null, ""))).isEqualTo(expected);
    }

    @Test
    void trimsDeduplicatesIgnoringCaseKeepsOrderAndCapsInterests() {
        List<String> interests = List.of(" Food ", "food", "music", "Sport", "movie", "tech", "animal", "health");

        assertThat(RagQueryBuilder.build(" Travel ", " A2 ", interests))
                .isEqualTo("Writing prompt. Topic: Travel. CEFR level: A2. Learner interests: Food, music, Sport, movie, tech.");
    }

    @Test
    void sameInputsGiveTheSameQuery() {
        assertThat(RagQueryBuilder.build("Travel", "A2", List.of("food")))
                .isEqualTo(RagQueryBuilder.build("Travel", "A2", List.of("food")));
    }

    @Test
    void requiresTopicAndLevel() {
        assertThatThrownBy(() -> RagQueryBuilder.build(" ", "A2", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RagQueryBuilder.build(null, "A2", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RagQueryBuilder.build("Travel", "", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interestsTextIsNoneWhenEmpty() {
        assertThat(RagQueryBuilder.interestsText(List.of())).isEqualTo("none");
        assertThat(RagQueryBuilder.interestsText(List.of("food", "Food", "music"))).isEqualTo("food, music");
    }
}
