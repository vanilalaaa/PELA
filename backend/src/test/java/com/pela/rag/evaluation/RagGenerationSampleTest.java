package com.pela.rag.evaluation;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PER-3 chất lượng: sinh 50 đề Writing (trải đều các cặp topic + level có đề active) bằng pipeline UC-24, ghi
 * build/evaluation/rag/generated.csv theo đúng cột của docs/evaluation/rag/generated.csv
 * (id, topic, level, prompt_text + 4 cột trống cho 2 người chấm). Chưa chạy được vì chưa có UC-24; xem docs/backlog.md
 * (P7). Chấm xong thì chạy {@link RagRatingReportTest}.
 */
@Tag("external")
@Disabled("Chờ UC-24 (P7)")
class RagGenerationSampleTest {

    static final int SAMPLE_SIZE = 50;
    static final String HEADER = "id,topic,level,prompt_text,on_topic_r1,right_level_r1,on_topic_r2,right_level_r2";

    @Test
    void generateFiftyPromptsForManualReview() {
        throw new UnsupportedOperationException("Chờ UC-24 (P7)");
    }
}
