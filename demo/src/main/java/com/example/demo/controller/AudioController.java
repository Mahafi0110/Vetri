package com.example.demo.controller;

import com.example.demo.service.AdminService;
import com.example.demo.service.AudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;

@RestController
@RequestMapping("/api/audio")
@RequiredArgsConstructor
public class AudioController {

    private final AudioService audioService;
    private final AdminService adminService; // ← ADD

    @PostMapping("/convert")
    public ResponseEntity<StreamingResponseBody> convertAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format) throws Exception {
        try {
            File result = audioService.convertAudio(file, format);
            log("Convert Audio", file, "success"); // ← ADD
            return buildStreamResponse(result, result.getName());
        } catch (Exception e) {
            log("Convert Audio", file, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/trim")
    public ResponseEntity<StreamingResponseBody> trimAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam("start") double start,
            @RequestParam("end") double end) throws Exception {
        try {
            File result = audioService.trimAudio(file, start, end);
            log("Trim Audio", file, "success"); // ← ADD
            return buildStreamResponse(result, result.getName());
        } catch (Exception e) {
            log("Trim Audio", file, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/compress")
    public ResponseEntity<StreamingResponseBody> compressAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bitrate", defaultValue = "128") int bitrate,
            @RequestParam(value = "format", defaultValue = "mp3") String format,
            @RequestParam(value = "sampleRate", defaultValue = "44100") String sampleRate) throws Exception {
        try {
            File result = audioService.compressAudio(file, bitrate, format, sampleRate);
            log("Compress Audio", file, "success"); // ← ADD
            return buildStreamResponse(result, result.getName());
        } catch (Exception e) {
            log("Compress Audio", file, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/merge")
    public ResponseEntity<StreamingResponseBody> mergeAudio(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "format", defaultValue = "mp3") String format,
            @RequestParam(value = "bitrate", defaultValue = "192") int bitrate,
            @RequestParam(value = "gap", defaultValue = "0.0") double gap) throws Exception {
        try {
            File result = audioService.mergeAudio(files, format, bitrate, gap);
            logMulti("Merge Audio", files, "success"); // ← ADD
            return buildStreamResponse(result, result.getName());
        } catch (Exception e) {
            logMulti("Merge Audio", files, "failed"); // ← ADD
            throw e;
        }
    }

    // ── LOG HELPERS ───────────────────────────
    private void log(String tool, MultipartFile file, String status) {
        try {
            adminService.logFileProcess(
                null, "Guest User",
                "guest@vetrifiles.com",
                tool, "audio",
                file.getOriginalFilename(),
                file.getSize(), status, "Unknown");
        } catch (Exception e) {
            System.err.println("Log failed: " + e.getMessage());
        }
    }

    private void logMulti(String tool, MultipartFile[] files, String status) {
        try {
            adminService.logFileProcess(
                null, "Guest User",
                "guest@vetrifiles.com",
                tool, "audio",
                files.length + " files",
                files[0].getSize(), status, "Unknown");
        } catch (Exception e) {
            System.err.println("Log failed: " + e.getMessage());
        }
    }

    // ── HELPER ───────────────────────────────────────────────────────────────
    private ResponseEntity<StreamingResponseBody> buildStreamResponse(File file, String filename) {
        StreamingResponseBody stream = outputStream -> {
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush(); // ✅ push each chunk immediately
                }
            } finally {
                file.delete(); // ✅ cleanup after stream finishes
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Accel-Buffering", "no") // ✅ disable Render proxy buffering
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(stream);
    }
}