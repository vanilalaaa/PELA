package com.pela.shared.application.port.out;

import java.util.List;

/** BR-15: lọc nội dung (do AI sinh hoặc do người dùng nhập) trước khi hiển thị. */
public interface ContentFilterPort {

    ContentCheck check(String text);

    /** @param matchedTerms từ/cụm từ cấm tìm thấy (chỉ để ghi nhận nội bộ, không hiển thị cho learner) */
    record ContentCheck(boolean allowed, List<String> matchedTerms) {
    }
}
