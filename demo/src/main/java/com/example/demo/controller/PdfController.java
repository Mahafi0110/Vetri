package com.example.demo.controller;

import com.example.demo.service.AdminService;
import com.example.demo.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfController {

    private final PdfService pdfService;
    private final AdminService adminService; // ← ADD

    @PostMapping("/compress")
    public ResponseEntity<StreamingResponseBody> compressPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "quality", defaultValue = "70") int quality) throws IOException {
        try {
            byte[] result = pdfService.compressPdf(file, quality);
            log("Compress PDF", file, "success"); // ← ADD
            return buildStreamResponse(result,
                    "compressed_" + file.getOriginalFilename(), "application/pdf");
        } catch (Exception e) {
            log("Compress PDF", file, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/merge")
    public ResponseEntity<StreamingResponseBody> mergePdfs(
            @RequestParam("files") MultipartFile[] files) throws IOException {
        try {
            byte[] result = pdfService.mergePdfs(files);
            logMulti("Merge PDFs", files, "success"); // ← ADD
            return buildStreamResponse(result, "merged_output.pdf", "application/pdf");
        } catch (Exception e) {
            logMulti("Merge PDFs", files, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/lock")
    public ResponseEntity<StreamingResponseBody> lockPdf(
            @RequestParam("file")                                           MultipartFile file,
            @RequestParam(value = "ownerPassword",   defaultValue = "")    String ownerPassword,
            @RequestParam(value = "userPassword",    defaultValue = "")    String userPassword,
            @RequestParam(value = "encryptionLevel", defaultValue = "128") String encryptionLevel,
            @RequestParam(value = "allowPrint",      defaultValue = "true")  boolean allowPrint,
            @RequestParam(value = "allowCopy",       defaultValue = "false") boolean allowCopy,
            @RequestParam(value = "allowModify",     defaultValue = "false") boolean allowModify,
            @RequestParam(value = "allowAnnotate",   defaultValue = "true")  boolean allowAnnotate) {
        try {
            byte[] result = pdfService.lockPdf(
                file, ownerPassword, userPassword,
                encryptionLevel, allowPrint, allowCopy,
                allowModify, allowAnnotate);
            log("Lock PDF", file, "success"); // ← ADD
            return buildStreamResponse(result,
                "locked_" + file.getOriginalFilename(), "application/pdf");
        } catch (Exception e) {
            log("Lock PDF", file, "failed"); // ← ADD
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/unlock")
    public ResponseEntity<StreamingResponseBody> unlockPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password) {
        try {
            byte[] result = pdfService.unlockPdf(file, password);
            log("Unlock PDF", file, "success"); // ← ADD
            return buildStreamResponse(result,
                "unlocked_" + file.getOriginalFilename(), "application/pdf");
        } catch (IllegalArgumentException e) {
            log("Unlock PDF", file, "failed"); // ← ADD
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log("Unlock PDF", file, "failed"); // ← ADD
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/split")
    public ResponseEntity<StreamingResponseBody> splitPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "range") String mode,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageRange", required = false) String pageRange,
            @RequestParam(value = "everyN", required = false) Integer everyN) throws IOException {
        try {
            if ("every".equalsIgnoreCase(mode)) {
                int n = everyN != null && everyN > 0 ? everyN : 1;
                byte[] result = pdfService.splitPdfEveryNAsZip(file, n);
                log("Split PDF", file, "success"); // ← ADD
                return buildStreamResponse(result, "split_parts.zip", "application/zip");
            }

            if (pageRange != null && !pageRange.isBlank()) {
                byte[] result = pdfService.extractPdfPagesByRange(file, pageRange);
                log("Split PDF", file, "success"); // ← ADD
                return buildStreamResponse(result,
                        "split_" + file.getOriginalFilename(), "application/pdf");
            }

            if (page != null && page > 0) {
                byte[] result = pdfService.splitPdf(file, page);
                log("Split PDF", file, "success"); // ← ADD
                return buildStreamResponse(result,
                        "split_" + file.getOriginalFilename(), "application/pdf");
            }

            log("Split PDF", file, "failed"); // ← ADD
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log("Split PDF", file, "failed"); // ← ADD
            throw e;
        }
    }

    @PostMapping("/add-signature")
    public ResponseEntity<StreamingResponseBody> addSignature(
            @RequestParam("file") MultipartFile file,
            @RequestParam("signature") MultipartFile signature,
            @RequestParam(value = "position", defaultValue = "bottom-center") String position,
            @RequestParam(value = "pageRange", defaultValue = "all") String pageRange,
            @RequestParam(value = "fromPage", required = false) Integer fromPage,
            @RequestParam(value = "toPage", required = false) Integer toPage,
            @RequestParam(value = "size", defaultValue = "100") float size,
            @RequestParam(value = "opacity", defaultValue = "100") float opacity) throws IOException {
        try {
            String resolvedRange = pageRange;
            if ("custom".equals(pageRange) && fromPage != null && toPage != null) {
                resolvedRange = "custom:" + fromPage + ":" + toPage;
            }

            byte[] result = pdfService.addSignature(
                    file, signature, position, resolvedRange, size, opacity);
            log("Add Signature", file, "success"); // ← ADD
            return buildStreamResponse(result,
                    "signed_" + file.getOriginalFilename(), "application/pdf");
        } catch (Exception e) {
            log("Add Signature", file, "failed"); // ← ADD
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
}