package com.pela.platform.adapter.out.gemini;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Đọc header RIFF/WAVE để lấy thời lượng (dataSize / byteRate). */
final class WavInfo {

    private WavInfo() {
    }

    static Duration duration(byte[] wav) {
        if (wav.length < 12 || !ascii(wav, 0, "RIFF") || !ascii(wav, 8, "WAVE")) {
            throw new IllegalArgumentException("Not a RIFF/WAVE file");
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int byteRate = 0;
        long dataSize = -1;
        int offset = 12;
        while (offset + 8 <= wav.length) {
            String id = new String(wav, offset, 4, StandardCharsets.US_ASCII);
            long size = Integer.toUnsignedLong(buffer.getInt(offset + 4));
            int body = offset + 8;
            if (id.equals("fmt ") && body + 12 <= wav.length) {
                byteRate = buffer.getInt(body + 8);
            } else if (id.equals("data")) {
                // Header có thể ghi kích thước lớn hơn thực tế (vd 0xFFFFFFFF khi stream): lấy phần thật có
                dataSize = Math.min(size, wav.length - body);
                break;
            }
            offset = (int) Math.min((long) body + size + (size % 2), wav.length);
        }
        if (byteRate <= 0 || dataSize < 0) {
            throw new IllegalArgumentException("WAV has no fmt/data chunk");
        }
        return Duration.ofNanos(Math.round(dataSize * 1_000_000_000.0 / byteRate));
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII).equals(expected);
    }
}
