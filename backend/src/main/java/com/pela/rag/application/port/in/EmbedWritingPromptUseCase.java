package com.pela.rag.application.port.in;

import java.util.UUID;

/**
 * BR-14: đề Writing được lưu cùng embedding trong cùng transaction với thao tác lưu/sửa của Admin. Module admin gọi
 * trong transaction của mình; lỗi embedding ({@code AiCallException}) làm transaction đó rollback.
 */
public interface EmbedWritingPromptUseCase {

    /**
     * Embed nội dung đề trước khi INSERT (đề active phải có embedding — chk_active_prompt_has_embedding). Người gọi
     * ghi {@code vector} và {@code model} vào question_bank.embedding_vector / embedding_model.
     */
    PromptEmbedding embedForStorage(String promptText);

    /** Sau khi sửa nội dung đề: embed lại question_text hiện tại và ghi vào chính dòng đó (cùng transaction của người gọi). */
    void reembed(UUID questionId);

    record PromptEmbedding(float[] vector, String model) {
    }
}
