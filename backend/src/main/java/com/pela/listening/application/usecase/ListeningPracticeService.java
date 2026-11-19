package com.pela.listening.application.usecase;

import com.pela.listening.application.port.in.ListeningPracticeUseCase;
import com.pela.listening.application.port.out.ListeningClipPort;
import com.pela.listening.application.port.out.ListeningClipPort.PlayableClip;
import com.pela.listening.application.port.out.ListeningClipPort.StoredClip;
import com.pela.listening.application.port.out.ListeningClipPort.StoredQuestion;
import com.pela.listening.application.port.out.ListeningCompletedEventPort;
import com.pela.listening.application.port.out.ListeningCompletedEventPort.ListeningCompleted;
import com.pela.listening.application.port.out.ListeningSessionPort;
import com.pela.listening.application.port.out.ListeningSessionPort.Attempt;
import com.pela.listening.application.port.out.ListeningSessionPort.NewSession;
import com.pela.listening.application.port.out.ListeningSessionPort.SaveOutcome;
import com.pela.listening.application.port.out.ListeningSessionPort.StoredSession;
import com.pela.listening.domain.ListeningClipValidator;
import com.pela.listening.domain.ListeningStars;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.IdGeneratorPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.application.port.out.StoragePort;
import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.ValidationFailedException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * UC-11: danh sách theo order_index (Listening.Select.Source), chi tiết bài không lộ đáp án/transcript, chấm lô đáp án
 * thô ở server (BR-23), sao theo BR-16 và chỉ cộng phần tăng khi làm lại (BR-36).
 */
@Service
public class ListeningPracticeService implements ListeningPracticeUseCase {

    static final String STAR_BANDS_SETTING = "star.bands";
    static final String AUDIO_URL_TTL_SETTING = "listening.audio_url_ttl_sec";
    static final String PENDING_AUDIO = "pending://";

    private final ListeningClipPort clips;
    private final ListeningSessionPort sessions;
    private final ListeningCompletedEventPort events;
    private final StoragePort storage;
    private final SettingsPort settings;
    private final ClockPort clock;
    private final IdGeneratorPort ids;

    public ListeningPracticeService(ListeningClipPort clips, ListeningSessionPort sessions,
            ListeningCompletedEventPort events, StoragePort storage, SettingsPort settings, ClockPort clock,
            IdGeneratorPort ids) {
        this.clips = clips;
        this.sessions = sessions;
        this.events = events;
        this.storage = storage;
        this.settings = settings;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public List<TopicItem> topics(String level) {
        return clips.topics(requireLevel(level)).entrySet().stream()
                .map(e -> new TopicItem(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public List<ClipItem> clips(UUID userId, String topic, String level) {
        if (topic == null || topic.isBlank()) {
            throw new ValidationFailedException("VALIDATION_FAILED", "topic is required", null);
        }
        List<PlayableClip> playable = clips.playableClips(topic.strip(), requireLevel(level));
        Map<UUID, Integer> best = sessions.bestStars(userId, playable.stream().map(PlayableClip::clipId).toList());
        return playable.stream()
                .map(c -> new ClipItem(c.clipId(), c.title(), c.orderIndex(), c.durationSec(), c.questionCount(),
                        best.get(c.clipId())))
                .toList();
    }

    @Override
    public ClipView clip(UUID userId, UUID clipId) {
        StoredClip clip = playable(clipId);
        Duration ttl = Duration.ofSeconds(settings.requiredInt(AUDIO_URL_TTL_SETTING));
        Instant now = clock.now();
        String url = storage.signedUrl(StoragePort.LISTENING_AUDIO_BUCKET, clip.audioPath(), ttl);
        List<QuestionView> questions = activeQuestions(clip).stream()
                .map(q -> new QuestionView(q.questionId(), q.type(), q.text(), q.options()))
                .toList();
        Integer best = sessions.bestStars(userId, List.of(clipId)).get(clipId);
        return new ClipView(clip.clipId(), clip.title(), clip.topic(), clip.cefrLevel(), clip.durationSec(), url,
                now.plus(ttl), questions, best);
    }

    @Override
    public SessionResult submit(UUID userId, SubmitCommand command) {
        if (command.sessionId() == null || command.clipId() == null) {
            throw new ValidationFailedException("VALIDATION_FAILED", "sessionId and clipId are required", null);
        }
        StoredSession existing = sessions.find(command.sessionId()).orElse(null);
        if (existing != null) {
            return replay(userId, command, existing);
        }

        StoredClip clip = playable(command.clipId());
        List<StoredQuestion> questions = activeQuestions(clip);
        Map<UUID, String> answers = answersByQuestion(command, questions);

        List<Attempt> attempts = new ArrayList<>();
        int correct = 0;
        for (StoredQuestion q : questions) {
            String selected = answers.get(q.questionId());
            boolean ok = selected.equals(q.answer());
            correct += ok ? 1 : 0;
            attempts.add(new Attempt(ids.newId(), q.questionId(), selected, ok));
        }
        int stars = ListeningStars.rate(correct, questions.size(), starBands());
        Instant now = clock.now();
        Instant startedAt = command.startedAt() != null && !command.startedAt().isAfter(now) ? command.startedAt() : now;

        SaveOutcome outcome = sessions.save(new NewSession(command.sessionId(), userId, clip.clipId(), questions.size(),
                correct, stars, startedAt, now, attempts));
        if (!outcome.created()) {
            // Gửi trùng đồng thời: bản kia đã được lưu
            return replay(userId, command, sessions.find(command.sessionId()).orElseThrow());
        }
        int added = ListeningStars.added(stars, outcome.previousBest());
        events.listeningCompleted(new ListeningCompleted(command.sessionId(), userId, clip.clipId(), stars, added, now));
        return result(clip, command.sessionId(), correct, questions.size(), stars, outcome.previousBest(), now,
                attempts);
    }

    /** Gửi lại cùng sessionId: trả kết quả đã lưu, không chấm và không cộng sao lần hai. */
    private SessionResult replay(UUID userId, SubmitCommand command, StoredSession s) {
        if (!s.userId().equals(userId) || !s.clipId().equals(command.clipId())) {
            throw new ConflictException("SESSION_ID_CONFLICT", "Session id is already used", null);
        }
        StoredClip clip = clips.find(s.clipId())
                .orElseThrow(() -> new NotFoundException("CLIP_NOT_FOUND", "Listening clip not found", null));
        int previousBest = sessions.bestStarsBefore(userId, s.clipId(), s.sessionId(), s.completedAt());
        return result(clip, s.sessionId(), s.correctCount(), s.totalQuestions(), s.stars(), previousBest,
                s.completedAt(), s.attempts());
    }

    private SessionResult result(StoredClip clip, UUID sessionId, int correct, int total, int stars, int previousBest,
            Instant completedAt, List<Attempt> attempts) {
        Map<UUID, StoredQuestion> byId = clip.questions().stream()
                .collect(Collectors.toMap(StoredQuestion::questionId, Function.identity()));
        List<UUID> order = clip.questions().stream().map(StoredQuestion::questionId).toList();
        List<QuestionResult> results = attempts.stream()
                .sorted(java.util.Comparator.comparingInt(a -> order.indexOf(a.questionId())))
                .map(a -> {
                    StoredQuestion q = byId.get(a.questionId());
                    return new QuestionResult(a.questionId(), a.selectedAnswer(), q == null ? null : q.answer(),
                            a.correct(), q == null ? null : q.explanation());
                })
                .toList();
        List<TranscriptLine> transcript = clip.transcript().stream()
                .map(l -> new TranscriptLine(l.speaker(), l.en(), l.vi()))
                .toList();
        return new SessionResult(sessionId, clip.clipId(), correct, total, stars, previousBest,
                ListeningStars.added(stars, previousBest), completedAt, results, transcript);
    }

    private static Map<UUID, String> answersByQuestion(SubmitCommand command, List<StoredQuestion> questions) {
        Set<UUID> expected = new HashSet<>(questions.stream().map(StoredQuestion::questionId).toList());
        Map<UUID, String> answers = new HashMap<>();
        for (Answer a : command.answers() == null ? List.<Answer>of() : command.answers()) {
            if (a == null || a.questionId() == null || !expected.contains(a.questionId())) {
                throw new ValidationFailedException("ANSWERS_MISMATCH", "Answer for an unknown question", null);
            }
            String selected = a.selectedAnswer() == null ? "" : a.selectedAnswer().strip().toUpperCase(Locale.ROOT);
            if (!ListeningClipValidator.ANSWERS.contains(selected)) {
                throw new ValidationFailedException("INVALID_ANSWER", "selectedAnswer must be one of A, B, C, D",
                        Map.of("questionId", a.questionId().toString()));
            }
            if (answers.put(a.questionId(), selected) != null) {
                throw new ValidationFailedException("ANSWERS_MISMATCH", "Question answered more than once",
                        Map.of("questionId", a.questionId().toString()));
            }
        }
        if (!answers.keySet().equals(expected)) {
            throw new ValidationFailedException("ANSWERS_MISMATCH", "Every question of the clip must be answered once",
                    Map.of("expected", expected.size(), "received", answers.size()));
        }
        return answers;
    }

    private StoredClip playable(UUID clipId) {
        return clips.find(clipId)
                .filter(c -> c.status().equals("published") && !c.audioPath().startsWith(PENDING_AUDIO))
                .filter(c -> !activeQuestions(c).isEmpty())
                .orElseThrow(() -> new NotFoundException("CLIP_NOT_FOUND", "Listening clip not found", null));
    }

    private static List<StoredQuestion> activeQuestions(StoredClip clip) {
        return clip.questions().stream().filter(q -> q.status().equals("active")).toList();
    }

    private List<ListeningStars.Band> starBands() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = settings.requiredJson(STAR_BANDS_SETTING, List.class);
        return raw.stream()
                .map(b -> new ListeningStars.Band(((Number) b.get("min_pct")).doubleValue(),
                        ((Number) b.get("stars")).intValue()))
                .toList();
    }

    private static String requireLevel(String level) {
        String normalized = level == null ? "" : level.strip().toUpperCase(Locale.ROOT);
        if (!ListeningClipValidator.CEFR_LEVELS.contains(normalized)) {
            throw new ValidationFailedException("VALIDATION_FAILED", "level must be one of A1, A2, B1, B2, C1, C2",
                    null);
        }
        return normalized;
    }
}
