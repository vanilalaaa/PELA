# 0005 — Listening sinh audio bằng TTS

- Trạng thái: Đã chấp nhận
- Liên quan: BR-37, MNT-3, MNT-4, ADR 0001

## Bối cảnh

Schema v1.3 thiết kế Listening theo luồng Admin upload file audio → STT ra transcript → LLM sinh câu hỏi → Admin sửa → lưu. Luồng này cần audio có bản quyền, tốn thời gian nhập liệu và phụ thuộc chất lượng STT. SRS đã được cập nhật: Listening chuyển sang sinh audio bằng TTS.

## Quyết định

- Admin import **file CSV theo mẫu**: lời thoại (theo người nói), câu hỏi và đáp án. `import_job.kind = 'listening_csv'`.
- Khi Admin lưu, backend gọi **Gemini TTS một lần** để sinh audio, lưu vào Supabase Storage và ghi `listening_clip.audio_url`. Không sinh lại mỗi lần learner nghe.
- `listening_clip.voice_config` ghi giọng đã dùng cho từng người nói, `tts_model` và `audio_format` ghi model/định dạng để biết khi nào cần sinh lại.
- Cấu hình nằm ở `system_setting`: `ai.model.tts` (chưa chọn model), `ai.tts.sample_rate`, `ai.tts.style_by_level`, `listening.max_speakers` (2), `listening.max_chars` (3000).
- Không còn upload audio/STT. Giữ giá trị `listening_audio` của `import_job.kind` và setting `import.max_audio_mb` để không phá dữ liệu cũ; luồng mới không dùng.

## Hệ quả

- Admin nhập Listening nhanh hơn, không cần file audio; transcript có sẵn từ CSV nên không còn bước sửa transcript.
- Chất lượng giọng phụ thuộc model TTS; số người nói bị giới hạn bởi khả năng multi-speaker của model.
- Bài nghe chưa sinh xong audio (`audio_url = 'pending://'`) không được phát cho learner.
- Đổi model TTS thì cần job sinh lại audio cho các clip có `tts_model` cũ.
- Cần chọn model TTS và điền `ai.model.tts` trước khi bật luồng import.
