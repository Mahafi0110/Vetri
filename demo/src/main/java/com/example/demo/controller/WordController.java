package com.example.demo.controller;

import com.example.demo.service.AdminService;
import com.example.demo.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/word")
public class WordController {

    @Autowired private WordService wordService;
    @Autowired private AdminService adminService; // ← ADD

    @PostMapping("/to-pdf")
    public ResponseEntity<StreamingResponseBody> convertWordToPdf(
            @RequestParam("file") MultipartFile file) throws Exception {
        try {
            byte[] result = wordService.convertToPdf(file);
            log("Word to PDF", file, "success"); // ← ADD
            String outputName = stripExtension(file.getOriginalFilename()) + ".pdf";
            return buildStreamResponse(result, outputName, "application/pdf");
        } catch (Exception e) {
            log("Word to PDF", file, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/to-html")
    public ResponseEntity<StreamingResponseBody> convertWordToHtml(
            @RequestParam("file") MultipartFile file) throws Exception {
        try {
            byte[] result = wordService.convertToHtml(file);
            log("Word to HTML", file, "success"); // ← ADD
            String outputName = stripExtension(file.getOriginalFilename()) + ".html";
            return buildStreamResponse(result, outputName, "text/html");
        } catch (Exception e) {
            log("Word to HTML", file, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/to-text")
    public ResponseEntity<StreamingResponseBody> convertWordToText(
            @RequestParam("file") MultipartFile file) throws Exception {
        try {
            byte[] result = wordService.extractText(file);
            log("Word to Text", file, "success"); // ← ADD
            String outputName = stripExtension(file.getOriginalFilename()) + ".txt";
            return buildStreamResponse(result, outputName, "text/plain");
        } catch (Exception e) {
            log("Word to Text", file, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/word-count")
    public ResponseEntity<Map<String, Object>> wordCount(
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(wordService.getWordCount(file));
    }

    @PostMapping("/metadata")
    public ResponseEntity<Map<String, Object>> metadata(
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(wordService.extractMetadata(file));
    }

    @PostMapping("/merge")
    public ResponseEntity<StreamingResponseBody> mergeDocuments(
            @RequestParam("files") MultipartFile[] files) throws Exception {
        try {
            byte[] result = wordService.mergeDocuments(files);
            logMulti("Merge Word Docs", files, "success"); // ← ADD
            return buildStreamResponse(result, "merged.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } catch (Exception e) {
            logMulti("Merge Word Docs", files, "failed"); // ← ADD
            throw e;
        }
    }

    // ── LOG HELPERS ───────────────────────────
    private void log(String tool, MultipartFile file, String status) {
        try {
            adminService.logFileProcess(
                null, "Guest User", "guest@vetrifiles.com",
                tool, "pdf", file.getOriginalFilename(),
                file.getSize(), status, "Unknown");
        } catch (Exception e) {
            System.err.println("Log failed: " + e.getMessage());
        }
    }

    private void logMulti(String tool, MultipartFile[] files, String status) {
        try {
            adminService.logFileProcess(
                null, "Guest User", "guest@vetrifiles.com",
                tool, "pdf", files.length + " files",
                files[0].getSize(), status, "Unknown");
        } catch (Exception e) {
            System.err.println("Log failed: " + e.getMessage());
        }
    }

    // ── HELPER ───────────────────────────────────────────────────────────────
    private ResponseEntity<StreamingResponseBody> buildStreamResponse(
            byte[] data, String filename, String contentType) {

        StreamingResponseBody stream = outputStream -> {
            try (InputStream in = new ByteArrayInputStream(data)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(data.length)
                .body(stream);
    }

    private static String stripExtension(String name) {
        if (name == null) return "output";
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }
}