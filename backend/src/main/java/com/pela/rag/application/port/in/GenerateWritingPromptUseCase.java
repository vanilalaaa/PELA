package com.pela.rag.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** UC-24: tạo đề Writing mới bằng RAG (RAG.Embed → Cache → Retrieve → Generate → Validate), BR-7, BR-8, BR-15, BR-24, BR-25. */
public interface GenerateWritingPromptUseCase {

    /**
     * Không bao giờ ném lỗi của nhà cung cấp AI: mọi sự cố (quá hạn, JSON sai, bị chặn, lỗi embedding…) trả về
     * {@link GenerationResult.NeedsFallback} kèm lý do để người gọi phát đề có sẵn (PER-4).
     */
    GenerationResult generate(UUID userId, GenerateCommand command);

    /**
     * @param cefrLevel trình độ của learner (người gọi đã lấy từ hồ sơ)
     * @param taskType  email | paragraph | essay; null = learner không chọn dạng bài (BR-24: khoá cache theo dạng bài
     *                  chỉ áp dụng khi có chọn; không chọn thì LLM giữ dạng bài của đề mẫu Top-1)
     * @param deadline  hạn chót của CẢ chuỗi (rag + chọn đề dự phòng): lúc nhận yêu cầu + ai.question_gen_p95_budget_ms.
     *                  LLM chỉ được chờ trong thời gian còn lại tới hạn này
     */
    record GenerateCommand(String topic, String cefrLevel, String taskType, Instant deadline) {
    }
}
