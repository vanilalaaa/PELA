package com.pela.listening.application.usecase;

import com.pela.listening.application.port.in.ListeningDrafts.GeneratedAudio;
import com.pela.listening.domain.AudioFingerprint;
import com.pela.listening.domain.ClipSpec;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.application.port.out.TtsPort;
import com.pela.shared.application.port.out.TtsPort.SpeechLine;
import com.pela.shared.application.port.out.TtsPort.SynthesizedAudio;
import com.pela.shared.domain.exception.ValidationFailedException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Sinh audio bài nghe bằng TtsPort (Admin.QuestionBank.Tts, AS-16): giọng theo người nói, cách đọc theo cấp CEFR. */
@Component
class ListeningAudioSynthesizer {

    static final String TTS_MODEL_SETTING = "ai.model.tts";
    static final String SAMPLE_RATE_SETTING = "ai.tts.sample_rate";
    static final String STYLE_SETTING = "ai.tts.style_by_level";
    /** TtsPort luôn trả WAV (RIFF, PCM). */
    static final String AUDIO_FORMAT = "wav";

    private final TtsPort tts;
    private final SettingsPort settings;

    ListeningAudioSynthesizer(TtsPort tts, SettingsPort settings) {
        this.tts = tts;
        this.settings = settings;
    }

    /** Lời thoại phải hợp lệ trước khi gọi; ở đây chỉ kiểm mỗi người nói đã có giọng. */
    GeneratedAudio synthesize(ClipSpec clip) {
        Map<String, String> voices = new HashMap<>();
        for (String speaker : clip.speakers()) {
            String voice = clip.voices().get(speaker);
            if (voice == null || voice.isBlank()) {
                throw new ValidationFailedException("VOICE_REQUIRED", "Speaker " + speaker + " has no voice",
                        Map.of("speaker", speaker));
            }
            voices.put(speaker, voice.strip());
        }
        List<SpeechLine> lines = clip.lines().stream()
                .map(l -> new SpeechLine(l.speaker().strip(), l.en().strip()))
                .toList();
        String level = clip.cefrLevel() == null ? "" : clip.cefrLevel().strip().toUpperCase(Locale.ROOT);
        @SuppressWarnings("unchecked")
        Map<String, String> styles = settings.json(STYLE_SETTING, Map.class).orElse(Map.of());
        int sampleRate = settings.requiredInt(SAMPLE_RATE_SETTING);
        String model = settings.requiredString(TTS_MODEL_SETTING);

        SynthesizedAudio audio = tts.synthesize(lines, voices, styles.get(level), sampleRate);
        int seconds = (int) Math.max(1, Math.ceil(audio.duration().toMillis() / 1000.0));
        return new GeneratedAudio(audio.wav(), seconds, model, AUDIO_FORMAT, AudioFingerprint.of(clip));
    }
}
