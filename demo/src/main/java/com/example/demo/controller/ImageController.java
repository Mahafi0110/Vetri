package com.example.demo.controller;

import com.example.demo.service.AdminService;
import com.example.demo.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;
    private final AdminService adminService; // ← ADD

    // ── COMPRESS ─────────────────────────────────────────────────────────────
    @PostMapping("/compress")
    public ResponseEntity<StreamingResponseBody> compressImage(
            @RequestParam("file")    MultipartFile file,
            @RequestParam(value = "quality", defaultValue = "0.7") float quality)
            throws Exception {
        try {
            byte[] result = imageService.compressImage(file, quality);
            log("Compress Image", file, "success"); // ← ADD
            return buildStreamResponse(result,
                    "compressed_" + file.getOriginalFilename(), "image/jpeg");
        } catch (Exception e) {
            log("Compress Image", file, "failed"); // ← ADD
            throw e;
        }
    }

    // ── RESIZE ───────────────────────────────────────────────────────────────
    @PostMapping("/resize")
    public ResponseEntity<StreamingResponseBody> resizeImage(
            @RequestParam("file")   MultipartFile file,
            @RequestParam("width")  int width,
            @RequestParam("height") int height)
            throws Exception {
        try {
            byte[] result = imageService.resizeImage(file, width, height);
            log("Resize Image", file, "success"); // ← ADD
            return buildStreamResponse(result,
                    "resized_" + file.getOriginalFilename(), "image/jpeg");
        } catch (Exception e) {
            log("Resize Image", file, "failed"); // ← ADD
            throw e;
        }
    }

    // ── CROP ─────────────────────────────────────────────────────────────────
    @PostMapping("/crop")
    public ResponseEntity<StreamingResponseBody> cropImage(
            @RequestParam("file")   MultipartFile file,
            @RequestParam("x")      int x,
            @RequestParam("y")      int y,
            @RequestParam("width")  int width,
            @RequestParam("height") int height)
            throws Exception {
        try {
            byte[] result = imageService.cropImage(file, x, y, width, height);
            log("Crop Image", file, "success"); // ← ADD
            return buildStreamResponse(result,
                    "cropped_" + file.getOriginalFilename(), "image/jpeg");
        } catch (Exception e) {
            log("Crop Image", file, "failed"); // ← ADD
            throw e;
        }
    }

    // ── CONVERT ──────────────────────────────────────────────────────────────
    @PostMapping("/convert")
    public ResponseEntity<StreamingResponseBody> convertImage(
            @RequestParam("file")   MultipartFile file,
            @RequestParam("format") String format)
            throws Exception {
        try {
            byte[] result = imageService.convertImage(file, format);
            log("Convert Image", file, "success"); // ← ADD
            String outputName = file.getOriginalFilename()
                    .replaceAll("\\.[^.]+$", "") + "." + format;
            return buildStreamResponse(result,
                    "converted_" + outputName, "image/" + format);
        } catch (Exception e) {
            log("Convert Image", file, "failed"); // ← ADD
            throw e;
        }
    }

    // ── WATERMARK ────────────────────────────────────────────────────────────
    @PostMapping("/watermark")
    public ResponseEntity<StreamingResponseBody> addWatermark(
            @RequestParam("file") MultipartFile file,
            @RequestParam("text") String text)
            throws Exception {
        try {
            byte[] result = imageService.addWatermark(file, text);
            log("Add Watermark", file, "success"); // ← ADD
            return buildStreamResponse(result,
                    "watermarked_" + file.getOriginalFilename(), "image/jpeg");
        } catch (Exception e) {
            log("Add Watermark", file, "failed"); // ← ADD
            throw e;
        }
    }

    // ── REMOVE BACKGROUND ────────────────────────────────────────────────────
    @PostMapping("/remove-background")
    public ResponseEntity<StreamingResponseBody> removeBackground(
            @RequestParam("file")                                    MultipartFile file,
            @RequestParam(value = "bgOption",   defaultValue = "transparent") String bgOption,
            @RequestParam(value = "bgColor",    defaultValue = "#ffffff")     String bgColor,
            @RequestParam(value = "refinement", defaultValue = "balanced")    String refinement,
            @RequestParam(value = "format",     defaultValue = "png")         String format)
            throws Exception {

        if (!format.matches("^(png|jpg|jpeg|webp)$")) format = "png";
        if (!bgOption.matches("^(transparent|white|black|color)$")) bgOption = "transparent";

        try {
            byte[] result = imageService.removeBackground(file, bgOption, bgColor, refinement, format);
            log("Remove Background", file, "success"); // ← ADD

            String outputName = file.getOriginalFilename()
                    .replaceAll("\\.[^.]+$", "") + "_no_bg." + format;

            String mimeType = "jpg".equals(format) || "jpeg".equals(format)
                    ? "image/jpeg" : "image/" + format;

            return buildStreamResponse(result, outputName, mimeType);
        } catch (Exception e) {
            log("Remove Background", file, "failed"); // ← ADD
            throw e;
        }
    }

    // ── TEST: verify remove.bg API key is loaded ──────────────────────────────
    @GetMapping("/test/removebg-key")
    public ResponseEntity<String> testRemoveBgKey(
            @Value("${app.removebg.api-key:NOT_SET}") String key) {
        if ("NOT_SET".equals(key) || key.isBlank()) {
            return ResponseEntity.ok("Key NOT set — add REMOVEBG_API_KEY to Render environment");
        }
        return ResponseEntity.ok("Key is set: " + key.substring(0, 4) + "**** length=" + key.length());
    }

    // ── LOG HELPER ────────────────────────────
    private void log(String tool, MultipartFile file, String status) {
        try {
            adminService.logFileProcess(
                null, "Guest User", "guest@vetrifiles.com",
                tool, "image", file.getOriginalFilename(),
                file.getSize(), status, "Unknown");
        } catch (Exception e) {
            System.err.println("Log failed: " + e.getMessage());
        }
    }

    // ── HELPER ───────────────────────────────────────────────────────────────
    private ResponseEntity<StreamingResponseBody> buildStreamResponse(
            byte[] data, String filename, String contentType) {

        StreamingResponseBody stream = outputStream -> {
            try (InputStream in = new ByteArrayInputStream(data)) {
                byte[] buffer = new byte[8192];
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
}