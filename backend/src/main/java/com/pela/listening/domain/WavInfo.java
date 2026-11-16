package com.pela.listening.domain;

/** Đọc thời lượng file WAV (RIFF/PCM) từ header: kích thước chunk {@code data} chia byte rate. */
public final class WavInfo {

    private WavInfo() {
    }

    /** Thời lượng làm tròn lên theo giây (tối thiểu 1); file không phải WAV hợp lệ → IllegalArgumentException. */
    public static int durationSeconds(byte[] wav) {
        if (wav.length < 12 || !ascii(wav, 0, "RIFF") || !ascii(wav, 8, "WAVE")) {
            throw new IllegalArgumentException("Not a RIFF/WAVE file");
        }
        long byteRate = -1;
        int offset = 12;
        while (offset + 8 <= wav.length) {
            long size = le32(wav, offset + 4);
            if (ascii(wav, offset, "fmt ") && offset + 20 <= wav.length) {
                byteRate = le32(wav, offset + 8 + 8);
            } else if (ascii(wav, offset, "data")) {
                if (byteRate <= 0) {
                    throw new IllegalArgumentException("WAV data chunk before fmt chunk");
                }
                long dataBytes = Math.min(size, wav.length - (offset + 8L));
                return (int) Math.max(1, (dataBytes + byteRate - 1) / byteRate);
            }
            offset += 8 + (int) size + (int) (size & 1);
        }
        throw new IllegalArgumentException("WAV has no data chunk");
    }

    private static boolean ascii(byte[] b, int offset, String tag) {
        for (int i = 0; i < 4; i++) {
            if (offset + i >= b.length || b[offset + i] != tag.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static long le32(byte[] b, int offset) {
        return (b[offset] & 0xFFL) | (b[offset + 1] & 0xFFL) << 8 | (b[offset + 2] & 0xFFL) << 16
                | (b[offset + 3] & 0xFFL) << 24;
    }
}
