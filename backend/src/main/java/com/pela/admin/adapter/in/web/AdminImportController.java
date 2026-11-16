package com.pela.admin.adapter.in.web;

import com.pela.admin.application.port.in.ListeningImportJobUseCase;
import com.pela.admin.application.port.in.ListeningImportJobUseCase.AudioPreview;
import com.pela.admin.application.port.in.ListeningImportJobUseCase.JobView;
import com.pela.admin.application.port.in.ListeningImportJobUseCase.SaveResult;
import com.pela.admin.application.port.in.ListeningImportJobUseCase.UploadCommand;
import com.pela.listening.application.port.in.ListeningDrafts.ClipDraft;
import com.pela.shared.adapter.in.web.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC-19: nhập Listening từ CSV (quyền Admin kiểm ở AdminAccessInterceptor cho mọi /v1/admin/**). Chỉ map request ↔
 * use case.
 */
@RestController
@RequestMapping("/v1/admin/imports")
public class AdminImportController {

    static final String LISTENING_TEMPLATE = "templates/listening-import-template.csv";

    private final ListeningImportJobUseCase imports;

    public AdminImportController(ListeningImportJobUseCase imports) {
        this.imports = imports;
    }

    /** 202: job queued, xử lý nền; theo dõi bằng GET /v1/admin/imports/{jobId}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobView upload(@CurrentUserId UUID adminId, @RequestParam String kind,
            @RequestParam("file") MultipartFile file) throws IOException {
        return imports.upload(adminId, new UploadCommand(kind, file.getOriginalFilename(), file.getContentType(),
                file.getBytes()));
    }

    @GetMapping("/{jobId}")
    public JobView get(@PathVariable UUID jobId) {
        return imports.get(jobId);
    }

    @PatchMapping("/{jobId}/preview")
    public JobView updatePreview(@CurrentUserId UUID adminId, @PathVariable UUID jobId,
            @Valid @RequestBody PreviewRequest request) {
        return imports.updatePreview(adminId, jobId, request.clips());
    }

    @PostMapping("/{jobId}/clips/{clipIndex}/tts")
    public AudioPreview generateAudio(@CurrentUserId UUID adminId, @PathVariable UUID jobId,
            @PathVariable int clipIndex) {
        return imports.generateAudio(adminId, jobId, clipIndex);
    }

    @PostMapping("/{jobId}/save")
    public SaveResult save(@CurrentUserId UUID adminId, @PathVariable UUID jobId) {
        return imports.save(adminId, jobId);
    }

    /** Admin.QuestionBank.ImportListening: tải file mẫu. */
    @GetMapping("/templates/listening")
    public ResponseEntity<byte[]> listeningTemplate() throws IOException {
        byte[] body;
        try (InputStream in = new ClassPathResource(LISTENING_TEMPLATE).getInputStream()) {
            body = in.readAllBytes();
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("listening-import-template.csv").build().toString())
                .body(body);
    }

    public record PreviewRequest(@NotNull List<ClipDraft> clips) {
    }
}
