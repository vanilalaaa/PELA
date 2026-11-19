package com.pela.admin.application.usecase;

import com.pela.admin.application.port.in.ListeningImportJobUseCase;
import com.pela.admin.application.port.out.ImportJobPort;
import com.pela.admin.application.port.out.ImportJobPort.ClipAudio;
import com.pela.admin.application.port.out.ImportJobPort.SourceFile;
import com.pela.admin.application.port.out.ImportJobPort.StoredClip;
import com.pela.admin.application.port.out.ImportJobPort.StoredJob;
import com.pela.admin.application.port.out.ImportTriggerPort;
import com.pela.listening.application.port.in.ListeningDrafts.ClipDraft;
import com.pela.listening.application.port.in.ListeningDrafts.GeneratedAudio;
import com.pela.listening.application.port.in.ListeningDrafts.Issue;
import com.pela.listening.application.port.in.ListeningDrafts.SavedAudio;
import com.pela.listening.application.port.in.ListeningImportUseCase;
import com.pela.listening.application.port.in.ListeningImportUseCase.ParsedImport;
import com.pela.shared.application.port.out.AdminAuditPort;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.IdGeneratorPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.application.port.out.StoragePort;
import com.pela.shared.application.port.out.TransactionPort;
import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.ValidationFailedException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** UC-19 / Admin.QuestionBank.ImportListening, .Tts, .Preview, .Save, .Limits (BR-14, BR-34, BR-35, BR-37). */
@Service
public class ListeningImportJobService implements ListeningImportJobUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListeningImportJobService.class);

    static final String MAX_CSV_MB_SETTING = "import.max_csv_mb";
    static final String PREVIEW_URL_TTL_SETTING = "listening.audio_url_ttl_sec";
    /** Kiểu MIME trình duyệt hay gửi cho file .csv. */
    private static final Set<String> CSV_TYPES = Set.of("text/csv", "application/csv", "text/plain",
            "application/vnd.ms-excel", "application/octet-stream");

    private final ImportJobPort jobs;
    private final ImportTriggerPort trigger;
    private final ListeningImportUseCase listening;
    private final StoragePort storage;
    private final AdminAuditPort audit;
    private final TransactionPort tx;
    private final SettingsPort settings;
    private final ClockPort clock;
    private final IdGeneratorPort ids;

    public ListeningImportJobService(ImportJobPort jobs, ImportTriggerPort trigger, ListeningImportUseCase listening,
            StoragePort storage, AdminAuditPort audit, TransactionPort tx, SettingsPort settings, ClockPort clock,
            IdGeneratorPort ids) {
        this.jobs = jobs;
        this.trigger = trigger;
        this.listening = listening;
        this.storage = storage;
        this.audit = audit;
        this.tx = tx;
        this.settings = settings;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public JobView upload(UUID adminId, UploadCommand command) {
        if (!KIND.equals(command.kind())) {
            throw new ValidationFailedException("UNSUPPORTED_IMPORT_KIND", "Only listening_csv imports are supported",
                    Map.of("kind", String.valueOf(command.kind())));
        }
        String filename = command.filename() == null ? "" : command.filename().strip();
        String type = command.contentType() == null ? "" : command.contentType().split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv") || (!type.isEmpty() && !CSV_TYPES.contains(type))) {
            throw new ValidationFailedException("UNSUPPORTED_FILE_TYPE", "Listening imports accept CSV files only", null);
        }
        byte[] content = command.content() == null ? new byte[0] : command.content();
        long maxBytes = (long) settings.requiredInt(MAX_CSV_MB_SETTING) * 1024 * 1024;
        if (content.length == 0) {
            throw new ValidationFailedException("FILE_EMPTY", "File is empty", null);
        }
        if (content.length > maxBytes) {
            throw new ValidationFailedException("FILE_TOO_LARGE", "File exceeds the size limit",
                    Map.of("maxBytes", maxBytes, "bytes", content.length));
        }

        UUID jobId = ids.newId();
        String path = "jobs/" + jobId + "/source.csv";
        storage.upload(StoragePort.IMPORTS_BUCKET, path, content, "text/csv");
        jobs.insert(jobId, KIND, new SourceFile(path, content.length, filename), adminId, clock.now());
        trigger.processSoon(jobId);
        return get(jobId);
    }

    @Override
    public JobView get(UUID jobId) {
        return view(load(jobId));
    }

    @Override
    public void process(UUID jobId) {
        if (!jobs.claim(jobId)) {
            return;
        }
        StoredJob job = load(jobId);
        try {
            byte[] bytes = storage.download(StoragePort.IMPORTS_BUCKET, job.file().path());
            String csv;
            try {
                csv = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                jobs.failed(jobId, "File is not UTF-8 encoded", List.of(
                        new Issue(null, null, null, "NOT_UTF8", "Save the CSV file as UTF-8")), clock.now());
                return;
            }
            ParsedImport parsed = listening.parseCsv(csv);
            if (!parsed.headerValid()) {
                // UC-19 E3: sai cột / file hỏng — không có preview, chỉ danh sách lỗi
                jobs.failed(jobId, "File does not match the listening template", parsed.issues(), clock.now());
                return;
            }
            if (parsed.clips().isEmpty()) {
                List<Issue> issues = new ArrayList<>(parsed.issues());
                issues.add(new Issue(null, null, null, "NO_CLIPS", "File has no listening clip"));
                jobs.failed(jobId, "File has no listening clip", issues, clock.now());
                return;
            }
            jobs.previewReady(jobId, parsed.clips().stream().map(c -> new StoredClip(c, null, null)).toList(),
                    parsed.issues(), clock.now());
        } catch (RuntimeException e) {
            log.warn("Listening import failed: job={}, {}", jobId, e.getClass().getSimpleName());
            jobs.failed(jobId, "Processing failed: " + e.getClass().getSimpleName(), List.of(), clock.now());
        }
    }

    @Override
    public int processQueued(int limit) {
        List<UUID> queued = jobs.queued(KIND, limit);
        queued.forEach(this::process);
        return queued.size();
    }

    @Override
    public JobView updatePreview(UUID adminId, UUID jobId, List<ClipDraft> clips) {
        StoredJob job = requirePreviewReady(jobId);
        if (clips == null || clips.isEmpty()) {
            throw new ValidationFailedException("NO_CLIPS", "Preview must have at least one clip", null);
        }
        List<StoredClip> updated = new ArrayList<>();
        for (int i = 0; i < clips.size(); i++) {
            // Audio giữ theo vị trí; không còn khớp lời thoại + giọng thì phải sinh lại trước khi lưu
            ClipAudio audio = i < job.clips().size() ? job.clips().get(i).audio() : null;
            updated.add(new StoredClip(clips.get(i), audio, null));
        }
        if (!jobs.replacePreview(jobId, updated, listening.validate(clips))) {
            throw notPreviewReady();
        }
        return get(jobId);
    }

    @Override
    public AudioPreview generateAudio(UUID adminId, UUID jobId, int clipIndex) {
        StoredJob job = requirePreviewReady(jobId);
        if (clipIndex < 0 || clipIndex >= job.clips().size()) {
            throw new NotFoundException("CLIP_NOT_FOUND", "No clip at this index", Map.of("clipIndex", clipIndex));
        }
        // UC-19 E3: bài còn lỗi thì chưa sinh audio (synthesize ném 422 LISTENING_CLIP_INVALID)
        GeneratedAudio audio = listening.synthesize(job.clips().get(clipIndex).clip());
        String path = "jobs/" + jobId + "/clip-" + clipIndex + "-" + audio.fingerprint() + ".wav";
        storage.upload(StoragePort.IMPORTS_BUCKET, path, audio.wav(), "audio/wav");
        int attempts = jobs.setClipAudio(jobId, clipIndex, new ClipAudio(path, audio.fingerprint(),
                audio.durationSec(), audio.ttsModel(), audio.format())).orElseThrow(this::notPreviewReady);
        String url = storage.signedUrl(StoragePort.IMPORTS_BUCKET, path,
                Duration.ofSeconds(settings.requiredInt(PREVIEW_URL_TTL_SETTING)));
        return new AudioPreview(clipIndex, url, audio.durationSec(), audio.fingerprint(), attempts);
    }

    @Override
    public SaveResult save(UUID adminId, UUID jobId) {
        StoredJob job = requirePreviewReady(jobId);
        List<ClipDraft> drafts = job.clips().stream().map(StoredClip::clip).toList();
        List<Issue> issues = listening.validate(drafts);
        if (!issues.isEmpty()) {
            throw new ValidationFailedException("IMPORT_HAS_ERRORS", "Fix every error before saving",
                    Map.of("issues", issues));
        }
        // BR-37: mỗi bài phải có audio sinh từ đúng lời thoại + giọng hiện tại
        List<Integer> outdated = new ArrayList<>();
        for (int i = 0; i < job.clips().size(); i++) {
            StoredClip clip = job.clips().get(i);
            if (clip.audio() == null || !clip.audio().fingerprint().equals(listening.audioFingerprint(clip.clip()))) {
                outdated.add(i);
            }
        }
        if (!outdated.isEmpty()) {
            throw new ConflictException("AUDIO_OUT_OF_DATE",
                    "Generate the audio of every clip from its current transcript and voices before saving",
                    Map.of("clipIndexes", outdated));
        }

        // Chép audio sang bucket listening-audio trước; transaction lỗi thì xoá lại
        List<UUID> clipIds = new ArrayList<>();
        List<String> copied = new ArrayList<>();
        try {
            for (StoredClip clip : job.clips()) {
                UUID clipId = ids.newId();
                String path = clipId + "/" + clip.audio().fingerprint() + "." + clip.audio().format();
                storage.copy(StoragePort.IMPORTS_BUCKET, clip.audio().path(), StoragePort.LISTENING_AUDIO_BUCKET, path);
                clipIds.add(clipId);
                copied.add(path);
            }
            tx.inTransaction(() -> {
                List<StoredClip> saved = new ArrayList<>();
                for (int i = 0; i < job.clips().size(); i++) {
                    StoredClip clip = job.clips().get(i);
                    UUID clipId = clipIds.get(i);
                    listening.createClip(clipId, adminId, jobId, clip.clip(), new SavedAudio(copied.get(i),
                            clip.audio().durationSec(), clip.audio().ttsModel(), clip.audio().format()));
                    audit.record(adminId, "listening_clip.create", "listening_clip", clipId.toString(),
                            Map.of("after", clipAudit(clip, jobId)));
                    saved.add(new StoredClip(clip.clip(), clip.audio(), clipId));
                }
                if (!jobs.markSaved(jobId, saved, clock.now())) {
                    throw notPreviewReady();
                }
                audit.record(adminId, "import_job.save", "import_job", jobId.toString(),
                        Map.of("before", Map.of("status", "preview_ready"),
                                "after", Map.of("status", "saved", "clipIds", clipIds)));
                return null;
            });
        } catch (RuntimeException e) {
            for (String path : copied) {
                try {
                    storage.delete(StoragePort.LISTENING_AUDIO_BUCKET, path);
                } catch (RuntimeException cleanup) {
                    log.warn("Could not remove copied audio after failed save: job={}", jobId);
                }
            }
            throw e;
        }
        return new SaveResult(jobId, clipIds);
    }

    private static Map<String, Object> clipAudit(StoredClip clip, UUID jobId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("title", clip.clip().title());
        after.put("topic", clip.clip().topic());
        after.put("cefrLevel", clip.clip().cefrLevel());
        after.put("questionCount", clip.clip().questions().size());
        after.put("voices", clip.clip().voices());
        after.put("audioFingerprint", clip.audio().fingerprint());
        after.put("importJobId", jobId.toString());
        return after;
    }

    private StoredJob requirePreviewReady(UUID jobId) {
        StoredJob job = load(jobId);
        if (!job.status().equals("preview_ready")) {
            throw notPreviewReady();
        }
        return job;
    }

    private StoredJob load(UUID jobId) {
        return jobs.find(jobId)
                .filter(j -> KIND.equals(j.kind()))
                .orElseThrow(() -> new NotFoundException("IMPORT_JOB_NOT_FOUND", "Import job not found", null));
    }

    private ConflictException notPreviewReady() {
        return new ConflictException("IMPORT_NOT_EDITABLE", "Import job is not in preview_ready status", null);
    }

    private JobView view(StoredJob job) {
        List<PreviewClip> clips = job.clips().stream()
                .map(c -> new PreviewClip(c.clip(), c.audio() == null ? null
                        : new ClipAudioView(c.audio().fingerprint(), c.audio().durationSec(), c.audio().ttsModel(),
                                c.audio().format(), c.audio().fingerprint().equals(listening.audioFingerprint(c.clip()))),
                        c.savedClipId()))
                .toList();
        return new JobView(job.jobId(), job.kind(), job.status(), job.attempts(), job.error(), job.createdAt(),
                job.finishedAt(), clips, job.warnings());
    }
}
