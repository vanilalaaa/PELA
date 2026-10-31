package com.pela.platform.adapter.out.contentfilter;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;

/** Dùng đúng hai file danh sách trong resources. */
class BlocklistContentFilterAdapterTest {

    private final BlocklistContentFilterAdapter filter = new BlocklistContentFilterAdapter(new DefaultResourceLoader(),
            List.of("classpath:content-filter/blocklist-en.txt", "classpath:content-filter/blocklist-vi.txt"));

    @ParameterizedTest
    @ValueSource(strings = {
            "I have a class at 8 am.",
            "Please read the passage and assess your answer.",
            "The bass player was a classic assassin in the movie.",
            "Scunthorpe is a town in England.",
            "Cocktail and peacock are words.",
            "Tôi đụng phải cái bàn.",
            "Con chó nhà tôi rất dễ thương.",
            "Mua hai lon bia và một ổ bánh mì.",
            "Chúng tôi đi du lịch Đà Nẵng.",
            "Khốn khó không làm anh nản lòng.",
            "",
    })
    void allowsNormalTextContainingBlockedSubstrings(String text) {
        assertThat(filter.check(text).allowed()).as(text).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "You are an ass.",
            "What the FUCK is this?",
            "shit!",
            "Mày là đồ chó.",
            "ĐỤ MÁ mày",
            "nói chuyện vcl",
            "địt   mẹ",
            "Thằng khốn nạn.",
    })
    void blocksOffensiveWordsInEnglishAndVietnamese(String text) {
        var check = filter.check(text);

        assertThat(check.allowed()).as(text).isFalse();
        assertThat(check.matchedTerms()).isNotEmpty();
    }

    @org.junit.jupiter.api.Test
    void vietnameseMatchesBothPrecomposedAndDecomposedForms() {
        String decomposed = Normalizer.normalize("đồ chó", Normalizer.Form.NFD);

        assertThat(filter.check("Mày là " + decomposed).allowed()).isFalse();
        assertThat(filter.check("Mày là " + decomposed).matchedTerms()).containsExactly("đồ chó");
    }
}
