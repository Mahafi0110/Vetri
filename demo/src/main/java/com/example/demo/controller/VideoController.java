package com.example.demo.controller;

import com.example.demo.service.AdminService;
import com.example.demo.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private static final int MAX_MERGE_CLIPS = 10;

    @Autowired private VideoService videoService;
    @Autowired private AdminService adminService;

    @PostMapping(value = "/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StreamingResponseBody> compressVideo(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "28") int crf,
            @RequestParam(required = false) String resolution,
            @RequestParam(required = false) String format
    ) throws Exception {
        File input = null;
        try {
            input = videoService.saveToTempFile(file);
            File output = videoService.compressVideo(input, crf, resolution, format);
            log("Compress Video", file, "success");
            return buildStreamResponse(output, "compressed.mp4", input, MediaType.APPLICATION_OCTET_STREAM);
        } catch (Exception e) {
            log("Compress Video", file, "failed");
            if (input != null) input.delete();
            throw e;
        }
    }

    // ── CONVERT FORMAT (NEW) ──────────────────
    // The Convert page also sends "crf" and "level"; we use "level"
    // (high / medium / low) so it matches Trim. Extra fields are simply ignored.
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StreamingResponseBody> convertVideo(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "mp4") String format,
            @RequestParam(defaultValue = "medium") String level,
            @RequestParam(defaultValue = "original") String resolution
    ) throws Exception {
        String fmt = videoService.safeVideoFormat(format);

        File input = null;
        try {
            input = videoService.saveToTempFile(file);
            File output = videoService.convertVideo(input, fmt, level, resolution);
            log("Convert Video", file, "success");
            return buildStreamResponse(output, "converted." + fmt, input, videoMediaTypeFor(fmt));
        } catch (Exception e) {
            log("Convert Video", file, "failed");
            if (input != null) input.delete();
            throw e;
        }
    }

    // ── MERGE VIDEOS (NEW) ────────────────────
    // The Merge page sends every clip under the same name, "files".
    // The order they arrive in is the order they are joined.
    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StreamingResponseBody> mergeVideos(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "mp4") String format,
            @RequestParam(defaultValue = "720") String resolution,
            @RequestParam(defaultValue = "medium") String level
    ) throws Exception {

        // Clear 400 error instead of a 500
        if (files == null || files.size() < 2 || files.size() > MAX_MERGE_CLIPS) {
            return ResponseEntity.badRequest().build();
        }

        String fmt = videoService.safeVideoFormat(format);

        List<File> inputs = new ArrayList<>();
        try {
            for (MultipartFile f : files) {
                inputs.add(videoService.saveToTempFile(f));
            }
            File output = videoService.mergeVideos(inputs, fmt, resolution, level);
            logMerge(files, "success");
            return buildStreamResponse(output, "merged." + fmt, inputs, videoMediaTypeFor(fmt));
        } catch (Exception e) {
            logMerge(files, "failed");
            for (File f : inputs) f.delete();
            throw e;
        }
    }

    // ── EXTRACT AUDIO ─────────────────────────
    // NEW: format, bitrate, sampleRate and channels are now accepted
    // (all optional, so older callers that send only "file" still work).
    // If the video has no sound, we answer 422 so the page can say so clearly.
    @PostMapping("/extract-audio")
    public ResponseEntity<StreamingResponseBody> extractAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "mp3") String format,
            @RequestParam(defaultValue = "192") String bitrate,
            @RequestParam(defaultValue = "44100") String sampleRate,
            @RequestParam(defaultValue = "2") String channels
    ) throws Exception {
        String fmt = videoService.normalizeAudioFormat(format);

        File input = null;
        try {
            input = videoService.saveToTempFile(file);
            File output = videoService.extractAudio(input, fmt, bitrate, sampleRate, channels);
            log("Extract Audio", file, "success");
            return buildStreamResponse(output, "audio." + fmt, input, audioMediaTypeFor(fmt));
        } catch (VideoService.NoAudioTrackException e) {
            log("Extract Audio", file, "failed");
            if (input != null) input.delete();
            return ResponseEntity.unprocessableEntity().build();
        } catch (Exception e) {
            log("Extract Audio", file, "failed");
            if (input != null) input.delete();
            throw e;
        }
    }

    // ── TRIM ──────────────────────────────────
    // "format" and "quality" are accepted (optional, so older
    // callers that only send file/start/end still work).
    @PostMapping("/trim")
    public ResponseEntity<StreamingResponseBody> trimVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam double start,
            @RequestParam double end,
            @RequestParam(defaultValue = "mp4") String format,
            @RequestParam(defaultValue = "medium") String quality
    ) throws Exception {

        // Basic checks so bad input gives a clear 400 error, not a 500
        if (start < 0 || end <= start) {
            return ResponseEntity.badRequest().build();
        }

        String fmt = format.trim().toLowerCase(Locale.ROOT);
        switch (fmt) {
            case "mp4": case "avi": case "mov": case "mkv": case "webm":
                break;
            default:
                fmt = "mp4";
        }

        File input = null;
        try {
            input = videoService.saveToTempFile(file);
            File output = videoService.trimVideo(input, start, end, fmt, quality);
            log("Trim Video", file, "success");
            return buildStreamResponse(output, "trimmed." + fmt, input, videoMediaTypeFor(fmt));
        } catch (Exception e) {
            log("Trim Video", file, "failed");
            if (input != null) input.delete();
            throw e;
        }
    }

    // ── CONTENT TYPE HELPERS ──────────────────
    private MediaType videoMediaTypeFor(String fmt) {
        switch (fmt) {
            case "mp4":  return MediaType.parseMediaType("video/mp4");
            case "mov":  return MediaType.parseMediaType("video/quicktime");
            case "mkv":  return MediaType.parseMediaType("video/x-matroska");
            case "webm": return MediaType.parseMediaType("video/webm");
            case "avi":  return MediaType.parseMediaType("video/x-msvideo");
            default:     return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private MediaType audioMediaTypeFor(String fmt) {
        switch (fmt) {
            case "mp3":  return MediaType.parseMediaType("audio/mpeg");
            case "aac":  return MediaType.parseMediaType("audio/aac");
            case "m4a":  return MediaType.parseMediaType("audio/mp4");
            case "ogg":  return MediaType.parseMediaType("audio/ogg");
            case "wav":  return MediaType.parseMediaType("audio/wav");
            case "flac": return MediaType.parseMediaType("audio/flac");
            default:     return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // ── LOG HELPERS ───────────────────────────
    private void log(String tool, MultipartFile file, String status) {
        try {
            adminService.logFileProcess(
                null, "Guest User", "guest@vetrifiles.com",
                tool, "video", file.getOriginalFilename(),
                file.getSize(), status, "Unknown");
        } catch (Exception e) {
            System.err.println("Log failed: " + e.getMessage());
        }
    }

    // One log line for the whole merge: "3 videos" with the combined size
    private void logMerge(List<MultipartFile> files, String status) {
        try {
            long total = 0;
            for (MultipartFile f : files) total += f.getSize();
            adminService.logFileProcess(
                null, "Guest User", "guest@vetrifiles.com",
                "Merge Video", "video", files.size() + " videos",
                total, status, "Unknown");
        } catch (Exception e) {
            System.err.println("Log failed: " + e.getMessage());
        }
    }

    // ── STREAM HELPERS ────────────────────────
    // Cleans up BOTH input and output after streaming
    private ResponseEntity<StreamingResponseBody> buildStreamResponse(
            File output, String filename, File input, MediaType type) {
        return buildStreamResponse(output, filename,
                input == null ? Collections.<File>emptyList() : Collections.singletonList(input),
                type);
    }

    // Same thing, for tools with several input files (Merge)
    private ResponseEntity<StreamingResponseBody> buildStreamResponse(
            File output, String filename, List<File> inputs, MediaType type) {
        StreamingResponseBody stream = outputStream -> {
            try (InputStream in = new FileInputStream(output)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            } finally {
                for (File f : inputs) {
                    if (f != null) f.delete();
                }
                output.delete();
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .header("X-Accel-Buffering", "no")
                .contentType(type)
                .contentLength(output.length())
                .body(stream);
    }
}