package com.pela.writing.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Báo một bài Writing vừa chấm thành công, gọi ngay sau khi transaction lưu kết quả commit. Gamification sẽ cài
 * adapter thật (star_event, streak BR-11, bộ đếm mở khoá Final Test BR-20, thông báo writing_graded); hiện là no-op.
 * Adapter phải idempotent theo submissionId vì job tạo bù có thể gửi lại.
 */
public interface WritingGradedEventPort {

    void writingGraded(WritingGraded event);

    record WritingGraded(UUID submissionId, UUID userId, int stars, Instant gradedAt) {
    }
}
