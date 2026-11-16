package com.pela.admin.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.admin.application.port.out.ImportJobPort;
import com.pela.listening.application.port.in.ListeningDrafts.Issue;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** import_job: files = [{path, bytes, filename}], preview = {clips: [{clip, audio, savedClipId}]}, warnings = [Issue]. */
@Component
public class JdbcImportJobAdapter implements ImportJobPort {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    record Preview(List<StoredClip> clips) {
    }

    private final JdbcTemplate jdbc;

    public JdbcImportJobAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID jobId, String kind, SourceFile file, UUID createdBy, Instant createdAt) {
        jdbc.update("""
                INSERT INTO import_job (job_id, kind, files, total_bytes, status, created_by, created_at)
                VALUES (?, ?, ?::jsonb, ?, 'queued', ?, ?)
                """, jobId, kind, write(List.of(file)), file.bytes(), createdBy, Timestamp.from(createdAt));
    }

    @Override
    public Optional<StoredJob> find(UUID jobId) {
        return jdbc.query("""
                SELECT job_id, kind, status, files::text, attempts, error, created_by, created_at, finished_at,
                       preview::text, warnings::text
                FROM import_job WHERE job_id = ?
                """, (rs, i) -> {
                    List<SourceFile> files = read(rs.getString(4), new TypeReference<List<SourceFile>>() { });
                    Preview preview = rs.getString(10) == null ? null : read(rs.getString(10), new TypeReference<Preview>() { });
                    List<Issue> warnings = rs.getString(11) == null ? List.of()
                            : read(rs.getString(11), new TypeReference<List<Issue>>() { });
                    return new StoredJob(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            files.isEmpty() ? null : files.getFirst(), rs.getInt(5), rs.getString(6),
                            rs.getObject(7, UUID.class), rs.getTimestamp(8).toInstant(),
                            rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(),
                            preview == null || preview.clips() == null ? List.of() : preview.clips(), warnings);
                }, jobId).stream().findFirst();
    }

    @Override
    public boolean claim(UUID jobId) {
        return jdbc.update("UPDATE import_job SET status = 'processing' WHERE job_id = ? AND status = 'queued'", jobId) > 0;
    }

    @Override
    public List<UUID> queued(String kind, int limit) {
        return jdbc.queryForList("SELECT job_id FROM import_job WHERE kind = ? AND status = 'queued' ORDER BY created_at LIMIT ?",
                UUID.class, kind, limit);
    }

    @Override
    public void previewReady(UUID jobId, List<StoredClip> clips, List<Issue> warnings, Instant at) {
        jdbc.update("""
                UPDATE import_job SET status = 'preview_ready', preview = ?::jsonb, warnings = ?::jsonb, error = NULL,
                                      finished_at = ?
                WHERE job_id = ? AND status = 'processing'
                """, write(new Preview(clips)), write(warnings), Timestamp.from(at), jobId);
    }

    @Override
    public void failed(UUID jobId, String error, List<Issue> warnings, Instant at) {
        jdbc.update("""
                UPDATE import_job SET status = 'failed', error = ?, warnings = ?::jsonb, finished_at = ?
                WHERE job_id = ? AND status = 'processing'
                """, error, write(warnings), Timestamp.from(at), jobId);
    }

    @Override
    public boolean replacePreview(UUID jobId, List<StoredClip> clips, List<Issue> warnings) {
        return jdbc.update("""
                UPDATE import_job SET preview = ?::jsonb, warnings = ?::jsonb
                WHERE job_id = ? AND status = 'preview_ready'
                """, write(new Preview(clips)), write(warnings), jobId) > 0;
    }

    @Override
    public Optional<Integer> setClipAudio(UUID jobId, int clipIndex, ClipAudio audio) {
        // jsonb_set trên đúng một phần tử: không ghi đè bản sửa preview chạy song song
        return jdbc.queryForList("""
                UPDATE import_job
                SET preview = jsonb_set(preview, ARRAY['clips', ?::text, 'audio'], ?::jsonb), attempts = attempts + 1
                WHERE job_id = ? AND status = 'preview_ready' AND jsonb_array_length(preview -> 'clips') > ?
                RETURNING attempts
                """, Integer.class, String.valueOf(clipIndex), write(audio), jobId, clipIndex).stream().findFirst();
    }

    @Override
    public boolean markSaved(UUID jobId, List<StoredClip> clips, Instant at) {
        return jdbc.update("""
                UPDATE import_job SET status = 'saved', preview = ?::jsonb, finished_at = ?
                WHERE job_id = ? AND status = 'preview_ready'
                """, write(new Preview(clips)), Timestamp.from(at), jobId) > 0;
    }

    private static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
