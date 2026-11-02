package com.pela.writing.application.port.in;

import java.util.UUID;

/** UC-23: chấm bài đã nộp (gọi từ job hoặc ngay sau khi nộp). */
public interface GradeWritingUseCase {

    /** Chấm một bài nếu còn cần chấm (pending, hoặc retry_queued đã tới hạn); an toàn khi gọi trùng. */
    void gradeOne(UUID submissionId);

    /** Chấm các bài tới hạn (pending chưa ai nhận, retry_queued đã tới next_retry_at). Trả số bài đã xử lý. */
    int gradeDue(int batchSize);
}
