package com.pela.writing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.writing.domain.WorthLearningWords.Candidate;
import com.pela.writing.domain.WorthLearningWords.CatalogueWord;
import com.pela.writing.domain.WorthLearningWords.Context;
import com.pela.writing.domain.WorthLearningWords.Reason;
import com.pela.writing.domain.WorthLearningWords.Selected;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorthLearningWordsTest {

    private static final UUID ADVICE_ID = UUID.randomUUID();

    private static Context context(CefrLevel learner, Map<String, CatalogueWord> catalogue, Set<String> inPool, int max) {
        return new Context(learner, Set.of("good"), catalogue, inPool, max);
    }

    private static Candidate candidate(String learnerForm, String word, String reason) {
        return candidate(learnerForm, word, reason, "B1");
    }

    private static Candidate candidate(String learnerForm, String word, String reason, String aiLevel) {
        return new Candidate(learnerForm, word, reason, "nghĩa", aiLevel);
    }

    @Test
    void keepsValidCandidatesAndLinksCatalogue() {
        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("advices", "advice", "wrong_form"), candidate("recieve", "receive", "misspelled")),
                context(CefrLevel.B1, Map.of("advice", new CatalogueWord(ADVICE_ID, CefrLevel.A2)), Set.of(), 3));

        assertThat(selected).extracting(Selected::word).containsExactly("advice", "receive");
        assertThat(selected.get(0).reason()).isEqualTo(Reason.WRONG_FORM);
        assertThat(selected.get(0).catalogueWord()).contains(new CatalogueWord(ADVICE_ID, CefrLevel.A2));
        assertThat(selected.get(0).cefrLevel()).isEqualTo(CefrLevel.A2);
        assertThat(selected.get(1).catalogueWord()).isEmpty();
    }

    @Test
    void betterAlternativeOnlyForRepeatedWords() {
        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("good", "excellent", "better_alternative"),
                        candidate("nice", "pleasant", "better_alternative")),
                context(CefrLevel.B1, Map.of(), Set.of(), 3));

        assertThat(selected).extracting(Selected::word).containsExactly("excellent");
    }

    @Test
    void dropsFunctionWordsProperNounsAndSameAsLearnerForm() {
        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("in", "on", "misused"),
                        candidate("in the", "on the", "misused"),
                        candidate("london", "London", "misspelled"),
                        candidate("Happy", "happy", "misused"),
                        candidate("make a photo", "take a photo", "misused")),
                context(CefrLevel.B1, Map.of(), Set.of(), 5));

        assertThat(selected).extracting(Selected::word).containsExactly("take a photo");
    }

    @Test
    void dropsCatalogueWordsMoreThanOneLevelAbove() {
        Map<String, CatalogueWord> catalogue = Map.of(
                "meticulous", new CatalogueWord(UUID.randomUUID(), CefrLevel.C1),
                "careful", new CatalogueWord(UUID.randomUUID(), CefrLevel.B1));

        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("carefull", "careful", "misspelled"), candidate("carefull", "meticulous", "misused")),
                context(CefrLevel.A2, catalogue, Set.of(), 3));

        assertThat(selected).extracting(Selected::word).containsExactly("careful");
    }

    @Test
    void usesAiLevelWhenWordIsNotInCatalogue() {
        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("x", "meticulous", "misused", "C1"), candidate("y", "careful", "misused", "B1")),
                context(CefrLevel.A2, Map.of(), Set.of(), 3));

        assertThat(selected).extracting(Selected::word).containsExactly("careful");
        assertThat(selected.get(0).cefrLevel()).isEqualTo(CefrLevel.B1);
    }

    @Test
    void catalogueLevelOverridesAiLevel() {
        Map<String, CatalogueWord> catalogue = Map.of("souvenir", new CatalogueWord(UUID.randomUUID(), CefrLevel.A2));

        List<Selected> selected = WorthLearningWords.select(List.of(candidate("sovenir", "souvenir", "misspelled", "C2")),
                context(CefrLevel.A2, catalogue, Set.of(), 3));

        assertThat(selected).singleElement().satisfies(w -> assertThat(w.cefrLevel()).isEqualTo(CefrLevel.A2));
    }

    @Test
    void learnerWithoutLevelCountsAsA1() {
        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("a", "advice", "wrong_form", "A2"), candidate("b", "receive", "misspelled", "B1")),
                context(null, Map.of(), Set.of(), 3));

        assertThat(selected).extracting(Selected::word).containsExactly("advice");
    }

    @Test
    void dropsOnlyCandidatesWithMissingOrInvalidFields() {
        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("a", "advice", "wrong_form", null),
                        candidate("b", "receive", "misspelled", "Z9"),
                        new Candidate("c", "weather", "misspelled", null, "A2"),
                        candidate("d", "x".repeat(101), "misspelled", "A2"),
                        candidate("e", "business", "misspelled", "a2")),
                context(CefrLevel.B1, Map.of(), Set.of(), 5));

        assertThat(selected).extracting(Selected::word).containsExactly("business");
    }

    @Test
    void dropsWordsAlreadyInPoolDuplicatesAndInvalidAndRespectsMax() {
        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("advices", "advice", "wrong_form"),
                        candidate("recieve", "receive", "misspelled"),
                        candidate("recieved", "Receive", "misspelled"),
                        candidate("x", "beautiful", "unknown_reason"),
                        new Candidate("x", "thing", "misused", " ", "A1"),
                        candidate("wether", "weather", "misspelled"),
                        candidate("buisness", "business", "misspelled")),
                context(CefrLevel.B1, Map.of(), Set.of("advice"), 2));

        assertThat(selected).extracting(Selected::word).containsExactly("receive", "weather");
    }
}
