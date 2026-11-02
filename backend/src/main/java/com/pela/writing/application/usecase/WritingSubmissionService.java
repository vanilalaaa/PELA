package com.pela.writing.application.usecase;

import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.roadmap.application.port.in.VocabularyCatalogUseCase;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.TooManyRequestsException;
import com.pela.shared.domain.exception.ValidationFailedException;
import com.pela.srs.application.port.in.ReviewQueueUseCase;
import com.pela.writing.application.port.in.AddSuggestedWordUseCase;
import com.pela.writing.application.port.in.SubmitWritingUseCase;
import com.pela.writing.application.port.in.WritingQueryUseCase;
import com.pela.writing.application.port.out.GradingTriggerPort;
import com.pela.writing.application.port.out.WritingPromptPort;
import com.pela.writing.application.port.out.WritingPromptPort.BankPrompt;
import com.pela.writing.application.port.out.WritingSubmissionPort;
import com.pela.writing.application.port.out.WritingSubmissionPort.Existing;
import com.pela.writing.application.port.out.WritingSubmissionPort.NewSubmission;
import com.pela.writing.application.port.out.WritingSubmissionPort.SuggestionRecord;
import com.pela.writing.domain.TextAnalysis;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** UC-10: nộp bài (Writing.Submit, BR-29), xem kết quả/lịch sử, thêm từ đáng học (BR-30). */
@Service
public class WritingSubmissionService implements SubmitWritingUseCase, WritingQueryUseCase, AddSuggestedWordUseCase {

    static final String DAILY_LIMIT_SETTING = "writing.daily_limit";
    /** Writing.Submit.Limit: 20–300 từ, hoặc khoảng hẹp hơn của đề. */
    static final int MIN_WORDS = 20;
    static final int MAX_WORDS = 300;
    private static final int MAX_HISTORY = 50;

    private final WritingSubmissionPort submissions;
    private final WritingPromptPort prompts;
    private final GradingTriggerPort trigger;
    private final GetCurrentUserUseCase users;
    private final VocabularyCatalogUseCase vocabulary;
    private final ReviewQueueUseCase reviewQueue;
    private final SettingsPort settings;
    private final ClockPort clock;

    public WritingSubmissionService(WritingSubmissionPort submissions, WritingPromptPort prompts,
            GradingTriggerPort trigger, GetCurrentUserUseCase users, VocabularyCatalogUseCase vocabulary,
            ReviewQueueUseCase reviewQueue, SettingsPort settings, ClockPort clock) {
        this.submissions = submissions;
        this.prompts = prompts;
        this.trigger = trigger;
        this.users = users;
        this.vocabulary = vocabulary;
        this.reviewQueue = reviewQueue;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public SubmitResult submit(UUID userId, SubmitCommand command) {
        Existing existing = submissions.find(command.submissionId()).orElse(null);
        if (existing != null && !existing.userId().equals(userId)) {
            throw new ConflictException("SUBMISSION_ID_CONFLICT", "Submission id is already used", null);
        }
        if (existing != null && !existing.status().equals("draft")) {
            // Gửi lại cùng submissionId (mạng chập chờn): trả trạng thái hiện tại, không nộp lần hai
            return new SubmitResult(existing.submissionId(), existing.status());
        }

        String promptSource = command.promptSource() == null ? "freeform" : command.promptSource();
        BankPrompt prompt = resolvePrompt(promptSource, command.promptQuestionId());
        String text = command.text() == null ? "" : command.text().strip();
        int wordCount = TextAnalysis.countWords(text);
        int min = prompt != null && prompt.minWords() != null ? Math.max(MIN_WORDS, prompt.minWords()) : MIN_WORDS;
        int max = prompt != null && prompt.maxWords() != null ? Math.min(MAX_WORDS, prompt.maxWords()) : MAX_WORDS;
        if (wordCount < min || wordCount > max) {
            throw new ValidationFailedException("WORD_COUNT_OUT_OF_RANGE", "Text must have " + min + "-" + max + " words",
                    Map.of("wordCount", wordCount, "minWords", min, "maxWords", max));
        }

        Instant now = clock.now();
        ZoneId zone = zoneOf(userId);
        LocalDate today = LocalDate.ofInstant(now, zone);
        int limit = settings.requiredInt(DAILY_LIMIT_SETTING);
        var usage = submissions.quotaUsage(userId, today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant());
        // BR-29: lượt bị giữ bởi bài nộp hôm nay (theo múi giờ learner) đã chấm hoặc đang chờ chấm; failed trả lượt
        String status = usage.used() >= limit ? "draft" : "pending";

        NewSubmission submission = new NewSubmission(command.submissionId(), userId, promptSource,
                prompt == null ? null : prompt.questionId(), prompt == null ? null : prompt.text(), text, wordCount,
                status, status.equals("pending") ? now : null);
        boolean stored = existing == null ? submissions.insert(submission) : submissions.updateDraft(submission);
        if (!stored) {
            // Bị gửi trùng đồng thời: bản kia đã được lưu
            Existing winner = submissions.find(command.submissionId()).orElseThrow();
            return new SubmitResult(winner.submissionId(), winner.status());
        }

        if (status.equals("draft")) {
            // E1 / BR-29: hết lượt chấm hôm nay, giữ bài dưới dạng nháp để nộp ngày mai
            Map<String, Object> details = new HashMap<>();
            details.put("submissionId", command.submissionId().toString());
            details.put("status", "draft");
            details.put("dailyLimit", limit);
            details.put("resetsAt", today.plusDays(1).atStartOfDay(zone).toInstant().toString());
            details.put("gradedToday", usage.gradedToday());
            details.put("awaitingGrading", usage.inFlight());
            String message = usage.inFlight() > 0
                    ? "Daily grading quota is used up; " + usage.inFlight()
                            + " submission(s) still awaiting grading hold a slot and return it if grading fails"
                    : "Daily grading quota is used up";
            throw new TooManyRequestsException("WRITING_QUOTA_EXCEEDED", message, details);
        }
        trigger.gradeSoon(command.submissionId());
        return new SubmitResult(command.submissionId(), "pending");
    }

    @Override
    public SubmissionView submission(UUID userId, UUID submissionId) {
        return submissions.view(userId, submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND", "Submission not found", null));
    }

    @Override
    public History history(UUID userId, int limit) {
        return submissions.history(userId, Math.max(1, Math.min(limit, MAX_HISTORY)));
    }

    @Override
    public AddedWord add(UUID userId, UUID suggestionId) {
        SuggestionRecord suggestion = submissions.suggestion(suggestionId)
                .filter(s -> s.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("SUGGESTION_NOT_FOUND", "Word suggestion not found", null));
        if (!suggestion.status().equals("suggested")) {
            if (suggestion.status().equals("dismissed")) {
                throw new ConflictException("SUGGESTION_DISMISSED", "Word suggestion was dismissed", null);
            }
            return new AddedWord(suggestionId, suggestion.status(), suggestion.vocabId(), suggestion.reviewCardId());
        }
        UUID vocabId = suggestion.vocabId() != null ? suggestion.vocabId()
                : vocabulary.addPrivateWord(userId, suggestion.word(), suggestion.meaningVi(), null, suggestion.cefrLevel());
        UUID cardId = reviewQueue.pushForReviewToday(userId, vocabId, LocalDate.ofInstant(clock.now(), zoneOf(userId)));
        submissions.markSuggestionAdded(suggestionId, "added", vocabId, cardId);
        return new AddedWord(suggestionId, "added", vocabId, cardId);
    }

    private BankPrompt resolvePrompt(String promptSource, UUID promptQuestionId) {
        return switch (promptSource) {
            case "freeform" -> null;
            case "bank" -> {
                if (promptQuestionId == null) {
                    throw new ValidationFailedException("VALIDATION_FAILED", "promptQuestionId is required for bank prompts",
                            null);
                }
                yield prompts.activePrompt(promptQuestionId)
                        .orElseThrow(() -> new NotFoundException("PROMPT_NOT_FOUND", "Writing prompt not found", null));
            }
            // UC-24 (đề do AI tạo) chưa có: chưa nhận bài cho loại đề này
            case "ai_generated" -> throw new ValidationFailedException("PROMPT_SOURCE_NOT_SUPPORTED",
                    "AI-generated prompts are not available yet", null);
            default -> throw new ValidationFailedException("VALIDATION_FAILED", "Unknown promptSource", null);
        };
    }

    private ZoneId zoneOf(UUID userId) {
        String timezone = users.currentUser(userId).timezone();
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }
}
