package com.pela.writing.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.identity.domain.UserAccount;
import com.pela.roadmap.application.port.in.VocabularyCatalogUseCase;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.ContentFilterPort;
import com.pela.shared.application.port.out.IdGeneratorPort;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.LlmPort.LlmResult;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.srs.application.port.in.ReviewQueueUseCase;
import com.pela.writing.application.port.in.GradeWritingUseCase;
import com.pela.writing.application.port.out.WritingPromptPort;
import com.pela.writing.application.port.out.WritingSubmissionPort;
import com.pela.writing.application.port.out.WritingSubmissionPort.Claimed;
import com.pela.writing.application.port.out.WritingSubmissionPort.GradedSubmission;
import com.pela.writing.application.port.out.WritingSubmissionPort.IssueRecord;
import com.pela.writing.application.port.out.WritingSubmissionPort.SuggestionToSave;
import com.pela.writing.domain.CefrLevel;
import com.pela.writing.domain.TextAnalysis;
import com.pela.writing.domain.TextAnalysis.RepeatedWord;
import com.pela.writing.domain.TextAnalysis.Sentence;
import com.pela.writing.domain.WorthLearningWords;
import com.pela.writing.domain.WorthLearningWords.CatalogueWord;
import com.pela.writing.domain.WritingGrading;
import com.pela.writing.domain.WritingGrading.AiIssue;
import com.pela.writing.domain.WritingGrading.CriterionScores;
import com.pela.writing.domain.WritingGrading.LocatedIssue;
import com.pela.writing.domain.WritingGrading.StarBand;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * UC-23: chấm bài bằng luật + một lần gọi LLM (thử lại 1 lần khi lỗi), kiểm tra và lọc kết quả, lưu điểm/sao, đẩy
 * từ đáng học vào hàng ôn (BR-9). Lỗi hai lần → retry_queued; hết lượt → failed. Không log nội dung bài viết.
 */
@Service
public class WritingGradingService implements GradeWritingUseCase {

    static final String TEMPLATE = "writing_grading";
    static final String TIMEOUT_SETTING = "ai.grading_timeout_ms";
    static final String MAX_ATTEMPTS_SETTING = "writing.grading_max_attempts";
    static final String RETRY_DELAY_SETTING = "writing.grading_retry_delay_minutes";
    static final String REPETITION_SETTING = "writing.repetition_min_count";
    static final String VOCAB_MAX_SETTING = "writing.vocab_suggestions_max";
    static final String STAR_BANDS_SETTING = "star.writing_bands";

    private static final Logger log = LoggerFactory.getLogger(WritingGradingService.class);

    private final WritingSubmissionPort submissions;
    private final WritingPromptPort prompts;
    private final LlmPort llm;
    private final ContentFilterPort contentFilter;
    private final GetCurrentUserUseCase users;
    private final VocabularyCatalogUseCase vocabulary;
    private final ReviewQueueUseCase reviewQueue;
    private final SettingsPort settings;
    private final ClockPort clock;
    private final IdGeneratorPort ids;

    public WritingGradingService(WritingSubmissionPort submissions, WritingPromptPort prompts, LlmPort llm,
            ContentFilterPort contentFilter, GetCurrentUserUseCase users, VocabularyCatalogUseCase vocabulary,
            ReviewQueueUseCase reviewQueue, SettingsPort settings, ClockPort clock, IdGeneratorPort ids) {
        this.submissions = submissions;
        this.prompts = prompts;
        this.llm = llm;
        this.contentFilter = contentFilter;
        this.users = users;
        this.vocabulary = vocabulary;
        this.reviewQueue = reviewQueue;
        this.settings = settings;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public int gradeDue(int batchSize) {
        List<UUID> due = submissions.dueForGrading(clock.now(), batchSize);
        due.forEach(this::gradeOne);
        return due.size();
    }

    @Override
    public void gradeOne(UUID submissionId) {
        Duration timeout = Duration.ofMillis(settings.requiredInt(TIMEOUT_SETTING));
        Instant now = clock.now();
        // Giữ bài đủ lâu cho 2 lần gọi AI; worker chết giữa chừng thì job nhận lại sau khi hết hạn giữ
        Claimed claimed = submissions.claim(submissionId, now, now.plus(timeout.multipliedBy(2)).plusSeconds(60))
                .orElse(null);
        if (claimed == null) {
            return;
        }
        UserAccount learner = users.currentUser(claimed.userId());
        String text = claimed.text();
        List<Sentence> sentences = TextAnalysis.splitSentences(text);
        List<RepeatedWord> repeated = TextAnalysis.repeatedContentWords(text, settings.requiredInt(REPETITION_SETTING));

        LlmResult<GradingResponse> result;
        try {
            Map<String, String> vars = promptVariables(claimed, learner, sentences, repeated);
            result = callWithOneRetry(vars, timeout);
        } catch (AiCallException e) {
            handleFailure(claimed, e);
            return;
        } catch (RuntimeException e) {
            // Lỗi cấu hình/lập trình (vd thiếu prompt active): không giữ bài mãi, tính như một lượt lỗi
            log.error("Writing grading error: submission={}, {}", claimed.submissionId(), e.getClass().getSimpleName());
            handleFailure(claimed, new AiCallException(AiCallException.Kind.UPSTREAM_ERROR, "grading error", e));
            return;
        }
        saveResult(claimed, learner, sentences, repeated, result);
    }

    private LlmResult<GradingResponse> callWithOneRetry(Map<String, String> vars, Duration timeout) {
        try {
            return llm.generateJson(TEMPLATE, vars, GradingResponse.class, timeout);
        } catch (AiCallException first) {
            if (first.kind() == AiCallException.Kind.SAFETY_BLOCKED) {
                throw first;
            }
            // Writing.Grade.Timeout: thử lại một lần
            return llm.generateJson(TEMPLATE, vars, GradingResponse.class, timeout);
        }
    }

    private void handleFailure(Claimed claimed, AiCallException e) {
        int maxAttempts = settings.requiredInt(MAX_ATTEMPTS_SETTING);
        // Bài bị bộ lọc an toàn chặn thì chấm lại cũng vô ích; hết lượt chấm cũng dừng. Không trừ quota (BR-29).
        if (e.kind() == AiCallException.Kind.SAFETY_BLOCKED || claimed.attempts() >= maxAttempts) {
            submissions.markFailed(claimed.submissionId());
            log.warn("Writing grading failed: submission={}, attempt={}, kind={}", claimed.submissionId(),
                    claimed.attempts(), e.kind());
        } else {
            Instant next = clock.now().plus(Duration.ofMinutes(settings.requiredInt(RETRY_DELAY_SETTING)));
            submissions.markRetryQueued(claimed.submissionId(), next);
            log.info("Writing grading queued for retry: submission={}, attempt={}, kind={}", claimed.submissionId(),
                    claimed.attempts(), e.kind());
        }
    }

    private Map<String, String> promptVariables(Claimed claimed, UserAccount learner, List<Sentence> sentences,
            List<RepeatedWord> repeated) {
        WritingPromptPort.BankPrompt prompt = claimed.promptQuestionId() == null ? null
                : prompts.activePrompt(claimed.promptQuestionId()).orElse(null);
        int min = prompt != null && prompt.minWords() != null ? Math.max(WritingSubmissionService.MIN_WORDS, prompt.minWords())
                : WritingSubmissionService.MIN_WORDS;
        int max = prompt != null && prompt.maxWords() != null ? Math.min(WritingSubmissionService.MAX_WORDS, prompt.maxWords())
                : WritingSubmissionService.MAX_WORDS;
        Map<String, String> vars = new HashMap<>();
        vars.put("level", learner.cefrLevel() == null ? "unknown (assume A2)" : learner.cefrLevel());
        vars.put("prompt_text", claimed.promptText() == null ? "(free writing, no prompt)" : claimed.promptText());
        vars.put("scoring_note", prompt == null || prompt.rubricHint() == null ? "(none)" : prompt.rubricHint());
        vars.put("min_words", String.valueOf(min));
        vars.put("max_words", String.valueOf(max));
        vars.put("word_count", String.valueOf(claimed.wordCount()));
        vars.put("numbered_sentences", sentences.stream()
                .map(s -> "[" + s.index() + "] " + s.text())
                .collect(Collectors.joining("\n")));
        vars.put("repeated_words", repeated.isEmpty() ? "(none)" : repeated.stream()
                .map(r -> r.word() + " (" + r.count() + "x)")
                .collect(Collectors.joining(", ")));
        return vars;
    }

    private void saveResult(Claimed claimed, UserAccount learner, List<Sentence> sentences, List<RepeatedWord> repeated,
            LlmResult<GradingResponse> result) {
        GradingResponse response = result.value();
        String text = claimed.text();

        // UC-23 bước 4: định vị issue, bỏ issue không định vị được, style = gợi ý
        List<LocatedIssue> located = WritingGrading.locate(text, sentences, response.issues().stream()
                .map(i -> new AiIssue(i.sentenceIndex(), i.original(), i.suggestion(), i.category(), i.severity(),
                        i.explanationVi()))
                .toList());
        // BR-15: lọc nội dung AI sinh trước khi lưu/hiển thị
        List<IssueRecord> issues = located.stream()
                .filter(i -> isClean(i.suggestion()) && isClean(i.explanationVi()))
                .map(i -> new IssueRecord(ids.newId(), i))
                .toList();
        List<String> rewrites = response.rewrites().stream().filter(r -> !r.isBlank() && isClean(r)).toList();
        boolean commentClean = isClean(response.comment());

        CriterionScores criteria = new CriterionScores(response.criteria().taskAchievement(),
                response.criteria().coherence(), response.criteria().vocabulary(), response.criteria().grammar());
        int score = criteria.total();
        int stars = WritingGrading.stars(score, Arrays.stream(settings.requiredJson(STAR_BANDS_SETTING, BandSetting[].class))
                .map(b -> new StarBand(b.minScore(), b.stars()))
                .toList());

        List<SuggestionToSave> suggestions = selectWords(claimed.userId(), learner, repeated, response);

        Map<String, Object> aiFeedback = new LinkedHashMap<>();
        aiFeedback.put("comment", commentClean ? response.comment() : null);
        aiFeedback.put("rewrites", rewrites);
        if (!commentClean) {
            aiFeedback.put("comment_filtered", true);
        }
        submissions.saveGraded(new GradedSubmission(claimed.submissionId(), score, criteria, stars, aiFeedback,
                result.templateVersion(), result.model(), clock.now(), issues, suggestions));
        log.info("Writing graded: submission={}, score={}, stars={}, issues={}, words={}, template v{}, model={}",
                claimed.submissionId(), score, stars, issues.size(), suggestions.size(), result.templateVersion(),
                result.model());
    }

    /** BR-30 chọn từ; từ có sẵn trong kho chung được đẩy vào hàng ôn hôm nay (BR-9), từ khác chờ learner bấm thêm. */
    private List<SuggestionToSave> selectWords(UUID userId, UserAccount learner, List<RepeatedWord> repeated,
            GradingResponse response) {
        List<String> words = response.vocabCandidates().stream().map(GradingResponse.VocabCandidate::word).toList();
        Map<String, CatalogueWord> catalogue = new HashMap<>();
        vocabulary.findCurated(words).forEach((word, entry) ->
                catalogue.put(word, new CatalogueWord(entry.vocabId(), CefrLevel.parse(entry.cefrLevel()))));
        Set<String> inPool = reviewQueue.wordsInReviewPool(userId, words);
        Set<String> repeatedWords = repeated.stream().map(RepeatedWord::word).collect(Collectors.toSet());

        List<WorthLearningWords.Selected> selected = WorthLearningWords.select(
                response.vocabCandidates().stream()
                        .filter(c -> isClean(c.meaningVi()))
                        .map(c -> new WorthLearningWords.Candidate(c.learnerForm(), c.word(), c.reason(), c.meaningVi()))
                        .toList(),
                new WorthLearningWords.Context(CefrLevel.parse(learner.cefrLevel()), repeatedWords, catalogue, inPool,
                        settings.requiredInt(VOCAB_MAX_SETTING)));

        LocalDate today = LocalDate.ofInstant(clock.now(), zoneOf(learner));
        List<SuggestionToSave> result = new ArrayList<>();
        for (WorthLearningWords.Selected word : selected) {
            if (word.catalogueWord().isPresent()) {
                UUID vocabId = word.catalogueWord().get().vocabId();
                UUID cardId = reviewQueue.pushForReviewToday(userId, vocabId, today);
                result.add(new SuggestionToSave(ids.newId(), userId, word, "auto_added", vocabId, cardId));
            } else {
                result.add(new SuggestionToSave(ids.newId(), userId, word, "suggested", null, null));
            }
        }
        return result;
    }

    private boolean isClean(String text) {
        return text == null || text.isBlank() || contentFilter.check(text).allowed();
    }

    private static ZoneId zoneOf(UserAccount learner) {
        try {
            return ZoneId.of(learner.timezone());
        } catch (Exception e) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }

    /** Một phần tử của setting star.writing_bands. */
    record BandSetting(@JsonProperty("min_score") int minScore, int stars) {
    }
}
