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
        return new Candidate(learnerForm, word, reason, "nghĩa");
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
    void unknownLevelsSkipCefrRule() {
        Map<String, CatalogueWord> catalogue = Map.of("meticulous", new CatalogueWord(UUID.randomUUID(), CefrLevel.C1));

        assertThat(WorthLearningWords.select(List.of(candidate("x", "meticulous", "misused")),
                context(null, catalogue, Set.of(), 3))).hasSize(1);
    }

    @Test
    void dropsWordsAlreadyInPoolDuplicatesAndInvalidAndRespectsMax() {
        List<Selected> selected = WorthLearningWords.select(
                List.of(candidate("advices", "advice", "wrong_form"),
                        candidate("recieve", "receive", "misspelled"),
                        candidate("recieved", "Receive", "misspelled"),
                        candidate("x", "beautiful", "unknown_reason"),
                        new Candidate("x", "thing", "misused", " "),
                        candidate("wether", "weather", "misspelled"),
                        candidate("buisness", "business", "misspelled")),
                context(CefrLevel.B1, Map.of(), Set.of("advice"), 2));

        assertThat(selected).extracting(Selected::word).containsExactly("receive", "weather");
    }
}
