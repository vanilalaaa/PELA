package com.pela.roadmap.application.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Kho từ vựng (bảng vocabulary) cho các module khác. */
public interface VocabularyCatalogUseCase {

    /** Từ (chữ thường) → mục trong kho chung (source = curated). Từ không có trong kho thì không có trong map. */
    Map<String, CatalogEntry> findCurated(Collection<String> words);

    /**
     * BR-30: thêm từ riêng của learner (source = writing, chỉ learner đó thấy). Idempotent theo (learner, từ);
     * trả vocab_id.
     */
    UUID addPrivateWord(UUID ownerId, String word, String meaningVi, String exampleSentence, String cefrLevel);

    /** @param cefrLevel A1–C2 hoặc null */
    record CatalogEntry(UUID vocabId, String cefrLevel) {
    }
}
