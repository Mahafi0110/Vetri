package com.example.demo.service;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/**
 * Core processing service.
 *
 * KEY DESIGN DECISIONS
 * ─────────────────────
 * 1. @Async → controller returns 202 immediately, this runs in background
 * 2. InputStream (never file.getBytes()) → only one chunk in memory at a time
 * 3. Temp file written to disk → avoids holding whole file in heap
 * 4. Temp file deleted in finally → never leaks disk space
 * 5. JobStatusStore updated throughout → frontend can poll for progress
 */
@Service
public class FileProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FileProcessingService.class);

    @Autowired
    private JobStatusStore jobStatusStore;

    @Value("${vetri.output.dir:./outputs}")
    private String outputDir;

    // ─── MAIN ENTRY POINT ─────────────────────────────────────────────────────

    /**
     * Called by the controller. Returns immediately (runs on fileProcessingExecutor).
     * The MultipartFile InputStream is transferred to a temp file first so the
     * HTTP request can complete without blocking processing.
     */
    @Async("fileProcessingExecutor")
    public void processAsync(MultipartFile file, String jobId) {

        Path tempFile = null;

        try {
            jobStatusStore.markProcessing(jobId);

            // Step 1 ── Stream upload to a temp file (no full-file byte[] anywhere)
            tempFile = streamToTempFile(file);

            // Step 2 ── Route to the correct processor by MIME type
            String contentType = file.getContentType();
            String resultUrl;

            if (contentType != null && contentType.equals("application/pdf")) {
                resultUrl = processPdf(tempFile, jobId);

            } else if (contentType != null && contentType.startsWith("video/")) {
                resultUrl = processVideo(tempFile, jobId, file.getOriginalFilename());

            } else if (contentType != null && contentType.startsWith("image/")) {
                resultUrl = processImage(tempFile, jobId, file.getOriginalFilename());

            } else {
                resultUrl = processGeneric(tempFile, jobId, file.getOriginalFilename());
            }

            jobStatusStore.markDone(jobId, resultUrl);
            log.info("Job {} completed → {}", jobId, resultUrl);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            jobStatusStore.markFailed(jobId, e.getMessage());

        } finally {
            // Step 3 ── ALWAYS delete the temp file, even on error
            deleteSilently(tempFile);
        }
    }

    // ─── STREAM TO TEMP FILE ──────────────────────────────────────────────────

    /**
     * Copies the upload InputStream to disk in 8KB chunks.
     * Peak heap usage ≈ 8KB, regardless of file size.
     */
    private Path streamToTempFile(MultipartFile file) throws IOException {
        Path temp = Files.createTempFile("vetri-", "-upload");

        try (InputStream in  = file.getInputStream();           // ✅ stream, not bytes
             OutputStream out = Files.newOutputStream(temp)) {

            byte[] buffer = new byte[8 * 1024]; // 8KB chunks
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        log.debug("Streamed {} bytes to temp file {}", Files.size(temp), temp);
        return temp;
    }

    // ─── PDF PROCESSING ───────────────────────────────────────────────────────

    /**
     * Uses PDFBox with streaming load (not full-document load).
     * For very large PDFs, consider processLargePdf() which reads page-by-page.
     */
    private String processPdf(Path tempFile, String jobId) throws IOException {
        String outputName = jobId + "-result.txt";
        Path   outputPath = resolveOutputPath(outputName);

        // PDFBox loads lazily when opened from a file path — much lower memory
        try (PDDocument doc = PDDocument.load(tempFile.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Page-by-page extraction (avoids loading all text into one String)
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                int totalPages = doc.getNumberOfPages();

                for (int page = 1; page <= totalPages; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String pageText = stripper.getText(doc);
                    writer.write(pageText);
                    writer.flush(); // flush each page; survives partial failures
                }
            }
        }

        return "/api/files/download/" + outputName;
    }

    // ─── VIDEO PROCESSING (FFmpeg) ────────────────────────────────────────────

    /**
     * Shells out to FFmpeg with low-memory flags.
     * FFmpeg must be installed on your server/container.
     *
     * Render: add FFmpeg in your Dockerfile or use a buildpack that includes it.
     */
    private String processVideo(Path tempFile, String jobId, String originalName) throws IOException, InterruptedException {
        String ext        = getExtension(originalName, "mp4");
        String outputName = jobId + "-result." + ext;
        Path   outputPath = resolveOutputPath(outputName);

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-i", tempFile.toString(),      // input
            "-vf", "scale=iw/2:ih/2",       // half resolution (example transform)
            "-preset", "ultrafast",         // low CPU usage
            "-threads", "1",                // ✅ single-thread → predictable memory
            "-bufsize", "1M",               // small output buffer
            "-y",                           // overwrite output without asking
            outputPath.toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Log FFmpeg output without buffering it all in RAM
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[FFmpeg job={}] {}", jobId, line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg exited with code " + exitCode);
        }

        return "/api/files/download/" + outputName;
    }

    // ─── IMAGE PROCESSING ─────────────────────────────────────────────────────

    private String processImage(Path tempFile, String jobId, String originalName) throws IOException {
        String ext        = getExtension(originalName, "jpg");
        String outputName = jobId + "-result." + ext;
        Path   outputPath = resolveOutputPath(outputName);

        // Example: just copy for now — replace with ImageIO or TwelveMonkeys logic
        Files.copy(tempFile, outputPath, StandardCopyOption.REPLACE_EXISTING);

        return "/api/files/download/" + outputName;
    }

    // ─── GENERIC FALLBACK ─────────────────────────────────────────────────────

    private String processGeneric(Path tempFile, String jobId, String originalName) throws IOException {
        String ext        = getExtension(originalName, "bin");
        String outputName = jobId + "-" + originalName;
        Path   outputPath = resolveOutputPath(outputName);

        Files.copy(tempFile, outputPath, StandardCopyOption.REPLACE_EXISTING);

        return "/api/files/download/" + outputName;
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private Path resolveOutputPath(String filename) throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        return dir.resolve(filename);
    }

    private void deleteSilently(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
                log.debug("Deleted temp file {}", path);
            } catch (IOException e) {
                log.warn("Could not delete temp file {}: {}", path, e.getMessage());
            }
        }
    }

    private String getExtension(String filename, String fallback) {
        if (filename == null) return fallback;
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : fallback;
    }
} 
    