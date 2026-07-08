package com.example.demo.service;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * WordService — auto-detects LibreOffice on Windows / Linux / macOS.
 *
 * Override detection by setting env variable:
 *   Windows:  set SOFFICE_PATH=C:\Program Files\LibreOffice\program\soffice.exe
 *   Linux:    export SOFFICE_PATH=/usr/bin/soffice
 */
@Service
public class WordService {

    private static final String SOFFICE_PATH = detectSofficePath();

    // ─────────────────────────────────────────────────────────────────────────
    //  LibreOffice path auto-detection
    // ─────────────────────────────────────────────────────────────────────────
    private static String detectSofficePath() {
        // 1. Environment variable takes priority
        String envPath = System.getenv("SOFFICE_PATH");
        if (envPath != null && !envPath.isBlank()) {
            System.out.println("[WordService] Using SOFFICE_PATH from env: " + envPath);
            return envPath;
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        System.out.println("[WordService] Detecting LibreOffice on OS: " + os);

        if (os.contains("win")) {
            String[] candidates = {
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe",
                "C:\\Program Files\\LibreOffice 7\\program\\soffice.exe",
                "C:\\Program Files\\LibreOffice 6\\program\\soffice.exe",
                "C:\\Program Files\\LibreOffice 24\\program\\soffice.exe",
                "C:\\Program Files\\LibreOffice 25\\program\\soffice.exe",
            };
            for (String p : candidates) {
                if (new File(p).exists()) {
                    System.out.println("[WordService] Found LibreOffice at: " + p);
                    return p;
                }
            }
            System.out.println("[WordService] LibreOffice not found in default locations. Falling back to 'soffice.exe'");
            return "soffice.exe";

        } else if (os.contains("mac")) {
            String[] candidates = {
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/usr/local/bin/soffice",
                "/opt/homebrew/bin/soffice",
            };
            for (String p : candidates) {
                if (new File(p).exists()) {
                    System.out.println("[WordService] Found LibreOffice at: " + p);
                    return p;
                }
            }
            return "soffice";

        } else {
            // Linux
            String[] candidates = {
                "/usr/bin/soffice",
                "/usr/local/bin/soffice",
                "/opt/libreoffice/program/soffice",
                "/opt/libreoffice7/program/soffice",
                "/snap/bin/libreoffice",
            };
            for (String p : candidates) {
                if (new File(p).exists()) {
                    System.out.println("[WordService] Found LibreOffice at: " + p);
                    return p;
                }
            }
            return "soffice";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  1.  Word → PDF   (LibreOffice)
    // ═══════════════════════════════════════════════════════════════════
    public byte[] convertToPdf(MultipartFile file) throws Exception {
        return runLibreOfficeConversion(file, "pdf");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2.  Word → HTML  (LibreOffice)
    // ═══════════════════════════════════════════════════════════════════
    public byte[] convertToHtml(MultipartFile file) throws Exception {
        return runLibreOfficeConversion(file, "html");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3.  Word → Plain Text  (Apache POI — no LibreOffice needed)
    // ═══════════════════════════════════════════════════════════════════
    public byte[] extractText(MultipartFile file) throws Exception {
        return extractRawText(file).getBytes(StandardCharsets.UTF_8);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4.  Word Count / Stats  (Apache POI — no LibreOffice needed)
    // ═══════════════════════════════════════════════════════════════════
    public Map<String, Object> getWordCount(MultipartFile file) throws Exception {
        String text = extractRawText(file);
        String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName",     file.getOriginalFilename());
        result.put("wordCount",    words.length);
        result.put("charCount",    text.length());
        result.put("charNoSpaces", text.replaceAll("\\s", "").length());
        result.put("lineCount",    text.split("\r?\n").length);

        if (isDocx(file)) {
            try (InputStream in = file.getInputStream();
                 XWPFDocument doc = new XWPFDocument(in)) {
                long nonEmpty = doc.getParagraphs().stream()
                        .filter(p -> !p.getText().trim().isEmpty()).count();
                result.put("paragraphCount", nonEmpty);
                result.put("tableCount",     doc.getTables().size());
            }
        } else {
            result.put("paragraphCount", "N/A for .doc");
            result.put("tableCount",     "N/A for .doc");
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5.  Metadata  (Apache POI — no LibreOffice needed)
    // ═══════════════════════════════════════════════════════════════════
    public Map<String, Object> extractMetadata(MultipartFile file) throws Exception {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fileName",   file.getOriginalFilename());
        meta.put("fileSizeKB", file.getSize() / 1024);

        if (isDocx(file)) {
            try (InputStream in = file.getInputStream();
                 XWPFDocument doc = new XWPFDocument(in)) {
                var core = doc.getProperties().getCoreProperties();
                meta.put("title",       nvl(core.getTitle()));
                meta.put("author",      nvl(core.getCreator()));
                meta.put("subject",     nvl(core.getSubject()));
                meta.put("description", nvl(core.getDescription()));
                meta.put("keywords",    nvl(core.getKeywords()));
                meta.put("created",     core.getCreated()  != null ? core.getCreated().toString()  : "N/A");
                meta.put("modified",    core.getModified() != null ? core.getModified().toString() : "N/A");
                meta.put("revision",    nvl(core.getRevision()));
            }
        } else {
            try (InputStream in = file.getInputStream();
                 HWPFDocument doc = new HWPFDocument(in)) {
                var si = doc.getSummaryInformation();
                meta.put("title",    nvl(si.getTitle()));
                meta.put("author",   nvl(si.getAuthor()));
                meta.put("subject",  nvl(si.getSubject()));
                meta.put("keywords", nvl(si.getKeywords()));
                meta.put("created",  si.getCreateDateTime()   != null ? si.getCreateDateTime().toString()   : "N/A");
                meta.put("modified", si.getLastSaveDateTime() != null ? si.getLastSaveDateTime().toString() : "N/A");
                meta.put("revision", String.valueOf(si.getRevNumber()));
            }
        }
        return meta;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  6.  Merge Documents → single .docx  (Apache POI — no LibreOffice)
    // ═══════════════════════════════════════════════════════════════════
    public byte[] mergeDocuments(MultipartFile[] files) throws Exception {
        if (files == null || files.length < 2)
            throw new IllegalArgumentException("Provide at least 2 files to merge.");

        try (XWPFDocument merged = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (int fi = 0; fi < files.length; fi++) {
                try (InputStream in = files[fi].getInputStream();
                     XWPFDocument doc = new XWPFDocument(in)) {

                    for (XWPFParagraph para : doc.getParagraphs()) {
                        XWPFParagraph np = merged.createParagraph();
                        np.setAlignment(para.getAlignment());
                        for (XWPFRun run : para.getRuns()) {
                            XWPFRun nr = np.createRun();
                            nr.setText(run.getText(0));
                            nr.setBold(run.isBold());
                            nr.setItalic(run.isItalic());
                            if (run.getFontSize() > 0) nr.setFontSize(run.getFontSize());
                            if (run.getColor() != null) nr.setColor(run.getColor());
                        }
                    }
                    if (fi < files.length - 1) {
                        merged.createParagraph().createRun().addBreak(BreakType.PAGE);
                    }
                }
            }
            merged.write(out);
            return out.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core LibreOffice runner
    // ─────────────────────────────────────────────────────────────────────────
    private byte[] runLibreOfficeConversion(MultipartFile file, String format) throws Exception {

        // Give a clear error if LO not installed
        File sofficeBin = new File(SOFFICE_PATH);
        boolean isAbsolutePath = sofficeBin.isAbsolute();
        if (isAbsolutePath && !sofficeBin.exists()) {
            throw new RuntimeException(
                "LibreOffice not found at: " + SOFFICE_PATH + "\n" +
                "Please install LibreOffice from https://www.libreoffice.org/download/ " +
                "and restart the application."
            );
        }

        String ext    = getExtension(file.getOriginalFilename());
        Path tmpDir   = Files.createTempDirectory("vetri-word-");
        Path tmpInput = tmpDir.resolve(UUID.randomUUID() + "." + ext);
        file.transferTo(tmpInput.toFile());

        try {
            // Unique user profile dir — prevents Windows lock conflicts when parallel requests
            Path userProfile = tmpDir.resolve("lo-profile");
            Files.createDirectories(userProfile);

            // file:///C:/... on Windows,  file:////home/... on Linux
            String profileUri = "file:///"
                + userProfile.toAbsolutePath().toString().replace("\\", "/");

            List<String> cmd = Arrays.asList(
                SOFFICE_PATH,
                "--headless",
                "--norestore",
                "--nofirststartwizard",
                "-env:UserInstallation=" + profileUri,
                "--convert-to", format,
                "--outdir", tmpDir.toAbsolutePath().toString(),
                tmpInput.toAbsolutePath().toString()
            );

            System.out.println("[WordService] Running: " + String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            // Linux/Mac: ensure HOME is set
            if (!System.getProperty("os.name","").toLowerCase().contains("win")) {
                pb.environment().putIfAbsent("HOME", System.getProperty("user.home", "/tmp"));
            }

            Process process = pb.start();
            String loOutput = new String(process.getInputStream().readAllBytes());
            int exitCode    = process.waitFor();

            System.out.println("[WordService] LO exit=" + exitCode + " output=" + loOutput);

            if (exitCode != 0) {
                throw new RuntimeException(
                    "LibreOffice exited with code " + exitCode + ". Output: " + loOutput);
            }

            // Locate output file (LO keeps the base name, just changes extension)
            String base    = tmpInput.getFileName().toString();
            String baseStem = base.substring(0, base.lastIndexOf('.'));
            Path outPath    = tmpDir.resolve(baseStem + "." + format);

            // LO sometimes produces .htm instead of .html
            if (!Files.exists(outPath) && "html".equals(format)) {
                Path htmPath = tmpDir.resolve(baseStem + ".htm");
                if (Files.exists(htmPath)) outPath = htmPath;
            }

            if (!Files.exists(outPath)) {
                throw new RuntimeException(
                    "Output file not found after conversion. LO said: " + loOutput);
            }

            return Files.readAllBytes(outPath);

        } finally {
            // Always clean up temp files
            try (var walk = Files.walk(tmpDir)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utilities
    // ─────────────────────────────────────────────────────────────────────────
    private String extractRawText(MultipartFile file) throws Exception {
        if (isDocx(file)) {
            try (InputStream in = file.getInputStream();
                 XWPFDocument doc = new XWPFDocument(in);
                 XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                return ex.getText();
            }
        } else {
            try (InputStream in = file.getInputStream();
                 HWPFDocument doc = new HWPFDocument(in);
                 WordExtractor ex = new WordExtractor(doc)) {
                return ex.getText();
            }
        }
    }

    private boolean isDocx(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".docx");
    }

    private String getExtension(String name) {
        if (name == null) return "docx";
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(i + 1).toLowerCase() : "docx";
    }

    private String nvl(String val) {
        return (val == null || val.isBlank()) ? "N/A" : val;
    }
}