package com.pela.shared.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sinh audio bài nghe bằng TTS (BR-37, ADR 0005). Model đọc từ {@code ai.model.tts}. Tối đa 2 người nói
 * trong một lần gọi (giới hạn của API). Lỗi ném {@link AiCallException}.
 */
public interface TtsPort {

    /**
     * @param lines      lời thoại theo thứ tự
     * @param voices     speaker → tên giọng dựng sẵn; mỗi speaker trong lines phải có giọng
     * @param style      chỉ dẫn giọng đọc (vd theo cấp CEFR); null = không chỉ định
     * @param sampleRate Hz, vd 24000
     */
    SynthesizedAudio synthesize(List<SpeechLine> lines, Map<String, String> voices, String style, int sampleRate);

    record SpeechLine(String speaker, String text) {
    }

    /** Audio WAV (RIFF, PCM) và thời lượng. */
    record SynthesizedAudio(byte[] wav, Duration duration) {

        @Override
        public String toString() {
            return "SynthesizedAudio[bytes=" + wav.length + ", duration=" + duration + "]";
        }
    }
}
