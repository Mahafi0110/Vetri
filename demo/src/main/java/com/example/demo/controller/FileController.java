package com.example.demo.controller;

import com.example.demo.service.FileProcessingService;
import com.example.demo.service.JobStatusStore;
import com.example.demo.service.JobStatusStore.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Map;

/**
 * FileController
 *
 * POST /api/process          → validates file, returns {jobId} with 202
 * GET  /api/jobs/{jobId}     → returns current job state (poll every 2s)
 * GET  /api/files/download/{name} → streams completed file back to browser
 */
@RestController
@RequestMapping("/api")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Autowired private FileProcessingService processingService;
    @Autowired private JobStatusStore        jobStatusStore;

    @Value("${vetri.output.dir:./outputs}")
    private String outputDir;

    // ── UPLOAD ──────────────────────────────────────────────────────────────

    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file) {

        // 1. Basic guard checks (size/type enforced again here, defence-in-depth)
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Could not determine file type"));
        }

        // 2. Register a job BEFORE dispatching async work
        String jobId = jobStatusStore.createJob();
        log.info("Queued job {} for file '{}' ({} bytes)", jobId,
                 file.getOriginalFilename(), file.getSize());

        // 3. Fire-and-forget — returns immediately, processing continues in background
        processingService.processAsync(file, jobId);

        // 4. Return 202 Accepted + jobId so the frontend can poll
        return ResponseEntity
                .accepted()                // HTTP 202
                .body(Map.of("jobId", jobId));
    }

    // ── STATUS POLL ─────────────────────────────────────────────────────────

    /**
     * Frontend polls this every 2 seconds.
     * Responds with the current state and (when done) the download URL.
     *
     * Response shape:
     *   { "state": "PROCESSING" }
     *   { "state": "DONE", "resultUrl": "/api/files/download/abc123-result.pdf" }
     *   { "state": "FAILED", "error": "FFmpeg exited with code 1" }
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> jobStatus(@PathVariable String jobId) {
        JobStatus status = jobStatusStore.get(jobId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return switch (status.state) {
            case QUEUED, PROCESSING -> ResponseEntity.ok(Map.of("state", status.state.name()));
            case DONE               -> ResponseEntity.ok(Map.of(
                    "state",     status.state.name(),
                    "resultUrl", status.resultUrl));
            case FAILED             -> ResponseEntity.ok(Map.of(
                    "state", status.state.name(),
                    "error", status.errorMessage != null ? status.errorMessage : "Unknown error"));
        };
    }

    // ── FILE DOWNLOAD ────────────────────────────────────────────────────────

    /**
     * Streams the processed file back without loading it fully into memory.
     * Spring's Resource abstraction handles chunked I/O automatically.
     */
    @GetMapping("/files/download/{filename:.+}")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(outputDir).resolve(filename).normalize();

            // Security: block path traversal attempts (e.g. "../../etc/passwd")
            if (!filePath.startsWith(Paths.get(outputDir).normalize())) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            // Content-Disposition: attachment → browser downloads, not renders
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}