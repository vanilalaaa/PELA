package com.pela.listening.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** BR-16 / BR-36 (sao), BR-37 (dấu vân tay audio), thời lượng WAV. */
class ListeningDomainTest {

    /** Giá trị mặc định của system_setting star.bands (AS-4). */
    private static final List<ListeningStars.Band> BANDS = List.of(new ListeningStars.Band(100, 5),
            new ListeningStars.Band(80, 4), new ListeningStars.Band(60, 3), new ListeningStars.Band(40, 2),
            new ListeningStars.Band(20, 1));

    @Test
    void starsFollowTheFiveTierAccuracyMapping() {
        assertThat(ListeningStars.rate(5, 5, BANDS)).isEqualTo(5);
        assertThat(ListeningStars.rate(4, 5, BANDS)).isEqualTo(4);
        assertThat(ListeningStars.rate(3, 5, BANDS)).isEqualTo(3);
        assertThat(ListeningStars.rate(2, 5, BANDS)).isEqualTo(2);
        assertThat(ListeningStars.rate(1, 5, BANDS)).isEqualTo(1);
        assertThat(ListeningStars.rate(0, 5, BANDS)).isZero();
        assertThat(ListeningStars.rate(2, 3, BANDS)).isEqualTo(3);
        assertThatThrownBy(() -> ListeningStars.rate(0, 0, BANDS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replayAddsOnlyTheImprovement() {
        // Ví dụ của BR-36: tốt nhất 3 sao, làm lại được 5 sao → cộng 2
        assertThat(ListeningStars.added(5, 3)).isEqualTo(2);
        assertThat(ListeningStars.added(2, 3)).isZero();
        assertThat(ListeningStars.added(3, 3)).isZero();
        assertThat(ListeningStars.added(4, 0)).isEqualTo(4);
    }

    @Test
    void fingerprintChangesWithTranscriptOrVoicesButNotTranslation() {
        List<ClipSpec.Line> lines = List.of(new ClipSpec.Line(2, "Linh", "Hello.", "Xin chào."));
        String base = AudioFingerprint.of(lines, Map.of("Linh", "Kore"));

        assertThat(AudioFingerprint.of(List.of(new ClipSpec.Line(null, "Linh", " Hello. ", "Chào bạn.")),
                Map.of("Linh", "Kore", "Unused", "Puck"))).isEqualTo(base);
        assertThat(AudioFingerprint.of(List.of(new ClipSpec.Line(2, "Linh", "Hello!", "Xin chào.")),
                Map.of("Linh", "Kore"))).isNotEqualTo(base);
        assertThat(AudioFingerprint.of(lines, Map.of("Linh", "Puck"))).isNotEqualTo(base);
    }

    @Test
    void wavDurationIsReadFromTheHeader() {
        assertThat(WavInfo.durationSeconds(wav(24000, 48000 * 2 + 10))).isEqualTo(3);
        assertThat(WavInfo.durationSeconds(wav(24000, 48000))).isEqualTo(1);
        assertThatThrownBy(() -> WavInfo.durationSeconds(new byte[] {1, 2, 3})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void csvReaderHandlesCrOnlyLineEndingsAndBom() {
        List<CsvReader.Record> records = CsvReader.read("﻿a,b\rc,\"d\"\r\n\ne,");

        assertThat(records).extracting(CsvReader.Record::cells)
                .containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e", ""));
        assertThat(records).extracting(CsvReader.Record::lineNumber).containsExactly(1, 2, 4);
    }

    /** WAV mono 16-bit PCM với {@code dataBytes} byte âm thanh. */
    static byte[] wav(int sampleRate, int dataBytes) {
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(sampleRate)
                .putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(dataBytes);
        return b.array();
    }
}
