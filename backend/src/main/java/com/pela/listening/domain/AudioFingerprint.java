package com.pela.listening.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dấu vân tay của nội dung được đọc thành audio (BR-37): người nói + câu tiếng Anh theo thứ tự và giọng của từng người
 * nói. Audio chỉ hợp lệ khi được sinh từ đúng dấu vân tay hiện tại; sửa lời thoại hoặc giọng thì phải sinh lại.
 * Bản dịch tiếng Việt không được đọc nên không nằm trong dấu vân tay.
 */
public final class AudioFingerprint {

    private AudioFingerprint() {
    }

    public static String of(List<ClipSpec.Line> lines, Map<String, String> voices) {
        StringBuilder canonical = new StringBuilder();
        for (ClipSpec.Line line : lines) {
            canonical.append("L\u001f").append(strip(line.speaker())).append('\u001f').append(strip(line.en()))
                    .append('\u001e');
        }
        // Chỉ giọng của người nói có trong lời thoại: giọng thừa không được đọc
        TreeMap<String, String> used = new TreeMap<>();
        voices.forEach((speaker, voice) -> {
            if (lines.stream().anyMatch(l -> strip(l.speaker()).equals(strip(speaker)))) {
                used.put(strip(speaker), strip(voice));
            }
        });
        used.forEach((speaker, voice) ->
                canonical.append("V\u001f").append(speaker).append('\u001f').append(voice).append('\u001e'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String of(ClipSpec clip) {
        return of(clip.lines(), clip.voices());
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
