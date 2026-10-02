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
import java.util.Locale;

@RestController
@RequestMapping("/api/video")
public class VideoController {

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

    // ── LOG HELPER ────────────────────────────
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

    // ── STREAM HELPER ─────────────────────────
    // Cleans up BOTH input and output after streaming
    private ResponseEntity<StreamingResponseBody> buildStreamResponse(
            File output, String filename, File input, MediaType type) {
        StreamingResponseBody stream = outputStream -> {
            try (InputStream in = new FileInputStream(output)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            } finally {
                if (input != null) input.delete();
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