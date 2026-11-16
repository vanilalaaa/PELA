package com.pela.listening.application.usecase;

import com.pela.listening.application.port.in.ListeningDrafts.ClipDraft;
import com.pela.listening.application.port.in.ListeningDrafts.GeneratedAudio;
import com.pela.listening.application.port.in.ListeningDrafts.Issue;
import com.pela.listening.application.port.in.ListeningDrafts.SavedAudio;
import com.pela.listening.application.port.in.ListeningImportUseCase;
import com.pela.listening.application.port.out.ListeningClipPort;
import com.pela.listening.application.port.out.ListeningClipPort.Line;
import com.pela.listening.application.port.out.ListeningClipPort.NewClip;
import com.pela.listening.application.port.out.ListeningClipPort.NewQuestion;
import com.pela.listening.domain.AudioFingerprint;
import com.pela.listening.domain.ClipSpec;
import com.pela.listening.domain.ImportIssue;
import com.pela.listening.domain.ListeningClipValidator;
import com.pela.listening.domain.ListeningClipValidator.Limits;
import com.pela.listening.domain.ListeningCsvParser;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.IdGeneratorPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.ValidationFailedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** UC-19: đọc + kiểm CSV Listening (BR-37), sinh audio, tạo bài nghe khi Admin lưu (BR-14). */
@Service
public class ListeningImportService implements ListeningImportUseCase {

    static final String MAX_SPEAKERS_SETTING = "listening.max_speakers";
    static final String MAX_CHARS_SETTING = "listening.max_chars";

    private final ListeningClipPort clips;
    private final ListeningAudioSynthesizer synthesizer;
    private final SettingsPort settings;
    private final ClockPort clock;
    private final IdGeneratorPort ids;

    public ListeningImportService(ListeningClipPort clips, ListeningAudioSynthesizer synthesizer, SettingsPort settings,
            ClockPort clock, IdGeneratorPort ids) {
        this.clips = clips;
        this.synthesizer = synthesizer;
        this.settings = settings;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public ParsedImport parseCsv(String csv) {
        ListeningCsvParser.Result parsed = ListeningCsvParser.parse(csv);
        List<Issue> issues = new ArrayList<>(parsed.issues().stream().map(ListeningDraftMapper::toIssue).toList());
        Limits limits = limits();
        for (int i = 0; i < parsed.clips().size(); i++) {
            ListeningClipValidator.validate(i, parsed.clips().get(i), limits).stream()
                    .map(ListeningDraftMapper::toIssue).forEach(issues::add);
        }
        return new ParsedImport(parsed.clips().stream().map(ListeningDraftMapper::toDraft).toList(), issues,
                parsed.headerValid());
    }

    @Override
    public List<Issue> validate(List<ClipDraft> drafts) {
        Limits limits = limits();
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ListeningClipValidator.validate(i, ListeningDraftMapper.toSpec(drafts.get(i)), limits).stream()
                    .map(ListeningDraftMapper::toIssue).forEach(issues::add);
        }
        return issues;
    }

    @Override
    public String audioFingerprint(ClipDraft clip) {
        return AudioFingerprint.of(ListeningDraftMapper.toSpec(clip));
    }

    @Override
    public GeneratedAudio synthesize(ClipDraft draft) {
        ClipSpec clip = requireValid(draft);
        return synthesizer.synthesize(clip);
    }

    @Override
    public UUID createClip(UUID clipId, UUID adminId, UUID importJobId, ClipDraft draft, SavedAudio audio) {
        ClipSpec clip = requireValid(draft);
        List<NewQuestion> questions = clip.questions().stream()
                .map(q -> new NewQuestion(ids.newId(), q.type().strip().toLowerCase(Locale.ROOT), q.text().strip(),
                        q.options().stream().map(String::strip).toList(),
                        q.answer().strip().toUpperCase(Locale.ROOT), q.explanation().strip()))
                .toList();
        List<Line> transcript = clip.lines().stream()
                .map(l -> new Line(l.speaker().strip(), l.en().strip(), l.vi().strip()))
                .toList();
        Map<String, String> voices = new LinkedHashMap<>();
        clip.speakers().forEach(s -> voices.put(s, clip.voices().get(s).strip()));
        clips.insert(new NewClip(clipId, clip.title().strip(), clip.topic().strip(),
                clip.cefrLevel().strip().toUpperCase(Locale.ROOT), audio.path(), audio.durationSec(), transcript,
                voices, audio.ttsModel(), audio.format(), importJobId, adminId, clock.now(), questions));
        return clipId;
    }

    private ClipSpec requireValid(ClipDraft draft) {
        ClipSpec clip = ListeningDraftMapper.toSpec(draft);
        List<ImportIssue> issues = ListeningClipValidator.validate(0, clip, limits());
        if (!issues.isEmpty()) {
            throw new ValidationFailedException("LISTENING_CLIP_INVALID", "Clip breaks BR-37",
                    Map.of("issues", issues.stream().map(ListeningDraftMapper::toIssue).toList()));
        }
        return clip;
    }

    Limits limits() {
        return new Limits(settings.requiredInt(MAX_SPEAKERS_SETTING), settings.requiredInt(MAX_CHARS_SETTING));
    }
}
