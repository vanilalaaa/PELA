package com.pela.rag.application.port.in;

import java.util.UUID;

/** BR-25: cache hết hạn sau 30 ngày hoặc ngay khi đề kho gốc bị retire. Entry hết hạn giữ lại để audit. */
public interface ExpireRagCacheUseCase {

    /**
     * Chuyển các entry active có source_template_id = {@code questionId} sang expired. Admin.QuestionBank.Retire PHẢI
     * gọi hàm này khi retire một đề Writing.
     *
     * @return số entry đã chuyển
     */
    int expireBySourceTemplate(UUID questionId);

    /** Chuyển các entry active quá expires_at sang expired (job hằng ngày). */
    int expireDue();
}
