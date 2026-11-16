package com.pela.listening.application.usecase;

import com.pela.listening.application.port.in.ListeningClipAdminUseCase;
import com.pela.listening.application.port.in.ListeningDrafts.GeneratedAudio;
import com.pela.listening.application.port.in.ListeningDrafts.LineDraft;
import com.pela.listening.application.port.out.ListeningClipPort;
import com.pela.listening.application.port.out.ListeningClipPort.ClipChange;
import com.pela.listening.application.port.out.ListeningClipPort.Line;
import com.pela.listening.application.port.out.ListeningClipPort.QuestionChange;
import com.pela.listening.application.port.out.ListeningClipPort.StoredClip;
import com.pela.listening.application.port.out.ListeningClipPort.StoredQuestion;
import com.pela.listening.domain.AudioFingerprint;
import com.pela.listening.domain.ClipSpec;
import com.pela.listening.domain.ImportIssue;
import com.pela.listening.domain.ListeningClipValidator;
import com.pela.listening.domain.WavInfo;
import com.pela.shared.application.port.out.AdminAuditPort;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.application.port.out.StoragePort;
import com.pela.shared.application.port.out.TransactionPort;
import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.ValidationFailedException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** UC-19: sửa / retire bài nghe đã lưu (BR-14, BR-34, BR-35, BR-37). */
@Service
public class ListeningClipAdminService implements ListeningClipAdminUseCase {

    private static final String TARGET = "listening_clip";

    private final ListeningClipPort clips;
    private final ListeningAudioSynthesizer synthesizer;
    private final ListeningImportService imports;
    private final StoragePort storage;
    private final AdminAuditPort audit;
    private final TransactionPort tx;
    private final SettingsPort settings;
    private final ClockPort clock;

    public ListeningClipAdminService(ListeningClipPort clips, ListeningAudioSynthesizer synthesizer,
            ListeningImportService imports, StoragePort storage, AdminAuditPort audit, TransactionPort tx,
            SettingsPort settings, ClockPort clock) {
        this.clips = clips;
        this.synthesizer = synthesizer;
        this.imports = imports;
        this.storage = storage;
        this.audit = audit;
        this.tx = tx;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public ClipDetail detail(UUID clipId) {
        return toDetail(load(clipId));
    }

    @Override
    public AudioPreview previewAudio(UUID adminId, UUID clipId, AudioRequest request) {
        StoredClip stored = load(clipId);
        ClipSpec clip = merged(stored, request.voices(), request.lines(), null, null);
        requireValid(clip);
        GeneratedAudio audio = synthesizer.synthesize(clip);
        storage.upload(StoragePort.IMPORTS_BUCKET, previewPath(clipId, audio.fingerprint()), audio.wav(), "audio/wav");
        String url = storage.signedUrl(StoragePort.IMPORTS_BUCKET, previewPath(clipId, audio.fingerprint()), urlTtl());
        return new AudioPreview(audio.fingerprint(), audio.fingerprint(), url, audio.durationSec());
    }

    @Override
    public ClipDetail update(UUID adminId, UUID clipId, UpdateCommand command) {
        StoredClip stored = load(clipId);
        if (stored.status().equals("retired")) {
            throw new ConflictException("CLIP_RETIRED", "Retired clips cannot be edited", null);
        }
        ClipSpec clip = merged(stored, command.voices(), command.lines(), command.title(), command.questions());
        requireValid(clip);

        String currentFingerprint = AudioFingerprint.of(spec(stored));
        String newFingerprint = AudioFingerprint.of(clip);
        String audioPath = null;
        Integer duration = null;
        String model = null;
        if (!newFingerprint.equals(currentFingerprint)) {
            // BR-37: lời thoại hoặc giọng đổi thì audio phải được sinh lại và nghe thử trước khi lưu
            if (command.audioToken() == null || !command.audioToken().equals(newFingerprint)) {
                throw new ConflictException("AUDIO_REGENERATION_REQUIRED",
                        "Transcript or voices changed: generate the audio again before saving",
                        Map.of("fingerprint", newFingerprint));
            }
            byte[] wav = storage.download(StoragePort.IMPORTS_BUCKET, previewPath(clipId, newFingerprint));
            duration = WavInfo.durationSeconds(wav);
            model = settings.requiredString(ListeningAudioSynthesizer.TTS_MODEL_SETTING);
            audioPath = clipId + "/" + newFingerprint + ".wav";
            storage.upload(StoragePort.LISTENING_AUDIO_BUCKET, audioPath, wav, "audio/wav");
        }

        Map<String, String> voices = new LinkedHashMap<>();
        clip.speakers().forEach(s -> voices.put(s, clip.voices().get(s).strip()));
        ClipChange change = new ClipChange(clip.title().strip(),
                clip.lines().stream().map(l -> new Line(l.speaker().strip(), l.en().strip(), l.vi().strip())).toList(),
                voices, audioPath, duration, model, audioPath == null ? null : ListeningAudioSynthesizer.AUDIO_FORMAT);
        Map<UUID, StoredQuestion> storedById = stored.questions().stream()
                .collect(Collectors.toMap(StoredQuestion::questionId, Function.identity()));
        List<QuestionChange> questionChanges = command.questions() == null ? List.of()
                : command.questions().stream().map(e -> {
                    StoredQuestion q = storedById.get(e.questionId());
                    return new QuestionChange(e.questionId(),
                            (e.text() != null ? e.text() : q.text()).strip(),
                            (e.options() != null ? e.options() : q.options()).stream().map(String::strip).toList(),
                            (e.answer() != null ? e.answer() : q.answer()).strip().toUpperCase(Locale.ROOT),
                            (e.explanation() != null ? e.explanation() : q.explanation()).strip());
                }).toList();

        ClipDetail before = toDetail(stored);
        return tx.inTransaction(() -> {
            clips.update(clipId, change, questionChanges);
            ClipDetail after = toDetail(load(clipId));
            audit.record(adminId, "listening_clip.update", TARGET, clipId.toString(),
                    Map.of("before", auditView(before), "after", auditView(after)));
            return after;
        });
    }

    @Override
    public ClipDetail retire(UUID adminId, UUID clipId) {
        StoredClip stored = load(clipId);
        if (stored.status().equals("retired")) {
            return toDetail(stored);
        }
        return tx.inTransaction(() -> {
            clips.retire(clipId, clock.now());
            audit.record(adminId, "listening_clip.retire", TARGET, clipId.toString(),
                    Map.of("before", Map.of("status", stored.status()), "after", Map.of("status", "retired")));
            return toDetail(load(clipId));
        });
    }

    /** Ghép bản sửa vào bài đã lưu; trường null giữ nguyên. Câu hỏi được nhận diện bằng questionId. */
    private static ClipSpec merged(StoredClip stored, Map<String, String> voices, List<LineDraft> lines, String title,
            List<QuestionEdit> edits) {
        ClipSpec base = spec(stored);
        Map<UUID, QuestionEdit> byId = edits == null ? Map.of()
                : edits.stream().filter(e -> e.questionId() != null)
                        .collect(Collectors.toMap(QuestionEdit::questionId, Function.identity(), (a, b) -> b));
        if (edits != null) {
            for (QuestionEdit e : edits) {
                if (e.questionId() == null || stored.questions().stream().noneMatch(q -> q.questionId().equals(e.questionId()))) {
                    throw new ValidationFailedException("QUESTION_NOT_IN_CLIP", "Question does not belong to this clip",
                            Map.of("questionId", String.valueOf(e.questionId())));
                }
            }
        }
        List<ClipSpec.Question> questions = stored.questions().stream()
                .filter(q -> q.status().equals("active"))
                .map(q -> {
                    QuestionEdit e = byId.get(q.questionId());
                    return new ClipSpec.Question(null, q.type(),
                            e != null && e.text() != null ? e.text() : q.text(),
                            e != null && e.options() != null ? e.options() : q.options(),
                            e != null && e.answer() != null ? e.answer() : q.answer(),
                            e != null && e.explanation() != null ? e.explanation() : q.explanation());
                })
                .toList();
        return new ClipSpec(null, null, title != null ? title : base.title(), base.topic(), base.cefrLevel(),
                voices != null ? voices : base.voices(),
                lines != null ? ListeningDraftMapper.lines(lines) : base.lines(), questions);
    }

    private static ClipSpec spec(StoredClip stored) {
        return new ClipSpec(null, null, stored.title(), stored.topic(), stored.cefrLevel(), stored.voices(),
                stored.transcript().stream().map(l -> new ClipSpec.Line(null, l.speaker(), l.en(), l.vi())).toList(),
                stored.questions().stream()
                        .map(q -> new ClipSpec.Question(null, q.type(), q.text(), q.options(), q.answer(),
                                q.explanation()))
                        .toList());
    }

    private void requireValid(ClipSpec clip) {
        List<ImportIssue> issues = ListeningClipValidator.validate(0, clip, imports.limits());
        if (!issues.isEmpty()) {
            throw new ValidationFailedException("LISTENING_CLIP_INVALID", "Clip breaks BR-37",
                    Map.of("issues", issues.stream().map(ListeningDraftMapper::toIssue).toList()));
        }
    }

    private StoredClip load(UUID clipId) {
        return clips.find(clipId)
                .orElseThrow(() -> new NotFoundException("CLIP_NOT_FOUND", "Listening clip not found", null));
    }

    private ClipDetail toDetail(StoredClip c) {
        String url = c.audioPath().startsWith(ListeningPracticeService.PENDING_AUDIO) ? null
                : storage.signedUrl(StoragePort.LISTENING_AUDIO_BUCKET, c.audioPath(), urlTtl());
        return new ClipDetail(c.clipId(), c.title(), c.topic(), c.cefrLevel(), c.orderIndex(), c.status(),
                c.durationSec(), c.ttsModel(), c.voices(),
                c.transcript().stream().map(l -> new LineDraft(null, l.speaker(), l.en(), l.vi())).toList(),
                c.questions().stream().map(q -> new QuestionDetail(q.questionId(), q.type(), q.text(), q.options(),
                        q.answer(), q.explanation(), q.status())).toList(),
                url, AudioFingerprint.of(spec(c)), c.retiredAt());
    }

    /** Nhật ký BR-35: nội dung bài (không có URL ký tạm). */
    private static Map<String, Object> auditView(ClipDetail d) {
        Map<String, Object> view = new HashMap<>();
        view.put("title", d.title());
        view.put("voices", d.voices());
        view.put("lines", d.lines());
        view.put("questions", d.questions());
        view.put("durationSec", d.durationSec());
        view.put("audioFingerprint", d.audioFingerprint());
        return view;
    }

    private static String previewPath(UUID clipId, String fingerprint) {
        return "clips/" + clipId + "/" + fingerprint + ".wav";
    }

    private Duration urlTtl() {
        return Duration.ofSeconds(settings.requiredInt(ListeningPracticeService.AUDIO_URL_TTL_SETTING));
    }
}
