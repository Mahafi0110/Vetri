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

@RestController
@RequestMapping("/api/video")
public class VideoController {

    @Autowired private VideoService videoService;
    @Autowired private AdminService adminService; // ← ADD

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
            log("Compress Video", file, "success"); // ← ADD
            return buildStreamResponse(output, "compressed.mp4", input);
        } catch (Exception e) {
            log("Compress Video", file, "failed"); // ← ADD
            if (input != null) input.delete();
            throw e;
        }
    }

    @PostMapping("/extract-audio")
    public ResponseEntity<StreamingResponseBody> extractAudio(
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        File input = null;
        try {
            input = videoService.saveToTempFile(file);
            File output = videoService.extractAudio(input, "mp3", "128", "44100", "2");
            log("Extract Audio", file, "success"); // ← ADD
            return buildStreamResponse(output, "audio.mp3", input);
        } catch (Exception e) {
            log("Extract Audio", file, "failed"); // ← ADD
            if (input != null) input.delete();
            throw e;
        }
    }

    @PostMapping("/trim")
    public ResponseEntity<StreamingResponseBody> trimVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam double start,
            @RequestParam double end
    ) throws Exception {
        File input = null;
        try {
            input = videoService.saveToTempFile(file);
            File output = videoService.trimVideo(input, start, end);
            log("Trim Video", file, "success"); // ← ADD
            return buildStreamResponse(output, "trimmed.mp4", input);
        } catch (Exception e) {
            log("Trim Video", file, "failed"); // ← ADD
            if (input != null) input.delete();
            throw e;
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
            File output, String filename, File input) {
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
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(output.length())
                .body(stream);
    }
}