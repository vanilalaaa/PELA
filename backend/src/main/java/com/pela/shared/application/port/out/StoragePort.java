package com.pela.shared.application.port.out;

import java.time.Duration;

/**
 * Lưu file ở Supabase Storage (bucket riêng tư). Chỉ backend gọi bằng service role key; client chỉ nhận URL ký tạm.
 * Lỗi kết nối / HTTP ném {@link StorageException}.
 */
public interface StoragePort {

    /** Bucket file Admin nhập (CSV, audio TTS nghe thử); không phát cho learner. */
    String IMPORTS_BUCKET = "imports";
    /** Bucket audio bài nghe đã lưu (BR-37). */
    String LISTENING_AUDIO_BUCKET = "listening-audio";

    /** Ghi đè nếu đã có. */
    void upload(String bucket, String path, byte[] content, String contentType);

    byte[] download(String bucket, String path);

    /** Sao chép object (có thể khác bucket); ghi đè nếu đích đã có. */
    void copy(String fromBucket, String fromPath, String toBucket, String toPath);

    /** Xoá; không có object thì bỏ qua. */
    void delete(String bucket, String path);

    /** URL tải trực tiếp, hết hạn sau {@code ttl}. */
    String signedUrl(String bucket, String path, Duration ttl);

    class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
