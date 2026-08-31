package com.example.demo.service;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.awt.Graphics2D;

@Service
public class PdfService {

    // ── MERGE PDFs ────────────────────────────
    public File mergePdfs(MultipartFile[] files) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        File output = File.createTempFile("merged_", ".pdf", new File(System.getProperty("java.io.tmpdir")));

        for (MultipartFile file : files) {
            try (InputStream is = file.getInputStream()) {
                PDDocument doc = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly());
                if (!doc.isEncrypted()) {
                    merger.addSource(file.getInputStream());
                }
                doc.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        merger.setDestinationFileName(output.getAbsolutePath());
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
        return output;
    }

    // ── LOCK PDF ──────────────────────────────
    public byte[] lockPdf(MultipartFile file,
            String ownerPassword, String userPassword,
            String encryptionLevel,
            boolean allowPrint, boolean allowCopy,
            boolean allowModify, boolean allowAnnotate)
            throws IOException {

        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly())) {

            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(allowPrint);
            ap.setCanExtractContent(allowCopy);
            ap.setCanModify(allowModify);
            ap.setCanModifyAnnotations(allowAnnotate);
            ap.setCanPrintDegraded(allowPrint);
            ap.setCanExtractForAccessibility(allowCopy);

            String effectiveOwner = ownerPassword.isEmpty() ? userPassword : ownerPassword;
            String effectiveUser  = userPassword;

            StandardProtectionPolicy policy =
                new StandardProtectionPolicy(effectiveOwner, effectiveUser, ap);

            int keyLength = "256".equals(encryptionLevel) ? 256 : 128;
            policy.setEncryptionKeyLength(keyLength);

            document.protect(policy);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    // ── UNLOCK PDF ────────────────────────────
    public byte[] unlockPdf(MultipartFile file, String password) throws IOException {
        try (InputStream is = file.getInputStream()) {
            PDDocument document;
            try {
                document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly());
                if (!document.isEncrypted()) {
                    document.close();
                    throw new IllegalArgumentException("PDF_NOT_ENCRYPTED");
                }
                document.close();
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                // PDF is encrypted — expected, continue
            }

            try (InputStream is2 = file.getInputStream();
                 PDDocument doc = PDDocument.load(is2, password,
                         MemoryUsageSetting.setupTempFileOnly())) {
                doc.setAllSecurityToBeRemoved(true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.save(out);
                return out.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException("WRONG_PASSWORD", e);
            }
        }
    }

    // ── SPLIT PDF (single page) ───────────────
    public byte[] splitPdf(MultipartFile file, int pageNumber) throws IOException {
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly())) {

            int totalPages = document.getNumberOfPages();
            if (pageNumber < 1 || pageNumber > totalPages) {
                throw new RuntimeException("Invalid page number: " + pageNumber);
            }

            try (PDDocument split = new PDDocument()) {
                split.addPage(document.getPage(pageNumber - 1));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                split.save(out);
                return out.toByteArray();
            }
        }
    }

    // ── SPLIT PDF EVERY N PAGES AS ZIP ───────
    public File splitPdfEveryNAsZip(MultipartFile file, int n) throws IOException {
        File zipOutput = File.createTempFile("split_parts_", ".zip", new File(System.getProperty("java.io.tmpdir")));

        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly());
             FileOutputStream fos = new FileOutputStream(zipOutput);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            int totalPages = document.getNumberOfPages();
            for (int i = 0; i < totalPages; i += n) {
                try (PDDocument part = new PDDocument()) {
                    for (int j = i; j < Math.min(i + n, totalPages); j++) {
                        part.addPage(document.getPage(j));
                    }
                    File partFile = File.createTempFile("part_", ".pdf", new File(System.getProperty("java.io.tmpdir")));
                    part.save(partFile);

                    ZipEntry entry = new ZipEntry("part_" + (i / n + 1) + ".pdf");
                    zos.putNextEntry(entry);
                    try (InputStream partIs = new FileInputStream(partFile)) {
                        byte[] buffer = new byte[8 * 1024];
                        int bytesRead;
                        while ((bytesRead = partIs.read(buffer)) != -1) {
                            zos.write(buffer, 0, bytesRead);
                        }
                    }
                    zos.closeEntry();
                    partFile.delete();
                }
            }
        }
        return zipOutput;
    }

    // ── EXTRACT PDF PAGES BY RANGE ────────────
    public File extractPdfPagesByRange(MultipartFile file, String pageRange) throws IOException {
        File output = File.createTempFile("split_", ".pdf", new File(System.getProperty("java.io.tmpdir")));

        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly());
             PDDocument extracted = new PDDocument()) {

            String[] ranges = pageRange.split(",");
            for (String range : ranges) {
                if (range.contains("-")) {
                    String[] parts = range.split("-");
                    int start = Integer.parseInt(parts[0].trim()) - 1;
                    int end = Integer.parseInt(parts[1].trim()) - 1;
                    for (int i = start; i <= end && i < document.getNumberOfPages(); i++) {
                        extracted.addPage(document.getPage(i));
                    }
                } else {
                    int page = Integer.parseInt(range.trim()) - 1;
                    if (page < document.getNumberOfPages()) {
                        extracted.addPage(document.getPage(page));
                    }
                }
            }
            extracted.save(output);
        }
        return output;
    }

    // ── COMPRESS PDF ──────────────────────────
    public File compressPdf(MultipartFile file, int quality) throws IOException {
        File tempOutput = File.createTempFile("compressed_", ".pdf");
        long originalSize = file.getSize();
        float imageQuality = Math.max(0.4f, Math.min(quality / 100f, 0.8f));

        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly())) {

            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) continue;

                for (COSName name : resources.getXObjectNames()) {
                    PDXObject xObject;
                    try { xObject = resources.getXObject(name); }
                    catch (Exception e) { continue; }

                    if (!(xObject instanceof PDImageXObject)) continue;

                    PDImageXObject image = (PDImageXObject) xObject;
                    if (image.getStream().getLength() < 10_000) continue;

                    BufferedImage buffered;
                    try { buffered = image.getImage(); }
                    catch (Exception e) { continue; }
                    if (buffered == null) continue;

                    try {
                        int newWidth  = Math.max(1, buffered.getWidth()  / 2);
                        int newHeight = Math.max(1, buffered.getHeight() / 2);

                        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = resized.createGraphics();
                        g.drawImage(buffered, 0, 0, newWidth, newHeight, null);
                        g.dispose();
                        buffered.flush();
                        buffered = null;

                        ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
                        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                        ImageWriteParam param = writer.getDefaultWriteParam();
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(imageQuality);

                        try (ImageOutputStream ios = ImageIO.createImageOutputStream(imgOut)) {
                            writer.setOutput(ios);
                            writer.write(null, new IIOImage(resized, null, null), param);
                        }
                        writer.dispose();
                        resized.flush();

                        byte[] newBytes = imgOut.toByteArray();
                        if (newBytes.length < image.getStream().getLength()) {
                            resources.put(name, JPEGFactory.createFromByteArray(document, newBytes));
                        }
                    } catch (Exception e) {
                        System.out.println("Skipping image: " + e.getMessage());
                    }
                }
            }

            document.save(tempOutput);
        }

        if (tempOutput.length() >= originalSize) {
            tempOutput.delete();
            File originalCopy = File.createTempFile("original_", ".pdf");
            file.transferTo(originalCopy);
            return originalCopy;
        }

        return tempOutput;
    }

    // ── ADD SIGNATURE ─────────────────────────
    public File addSignature(MultipartFile pdfFile,
                              MultipartFile signatureFile,
                              String position,
                              String pageRange,
                              float sigSize,
                              float opacity) throws IOException {

        File output = File.createTempFile("signed_", ".pdf", new File(System.getProperty("java.io.tmpdir")));

        try (InputStream pdfIs = pdfFile.getInputStream();
             InputStream sigIs = signatureFile.getInputStream();
             PDDocument document = PDDocument.load(pdfIs, MemoryUsageSetting.setupTempFileOnly())) {

            BufferedImage sigImage = ImageIO.read(sigIs);
            if (sigImage == null) throw new RuntimeException("Invalid signature image");

            int totalPages = document.getNumberOfPages();
            boolean[] pagesToSign = new boolean[totalPages];

            if (pageRange == null || pageRange.equals("all")) {
                for (int i = 0; i < totalPages; i++) pagesToSign[i] = true;
            } else if (pageRange.equals("current")) {
                if (totalPages > 0) pagesToSign[0] = true;
            } else if (pageRange.startsWith("custom:")) {
                String[] parts = pageRange.split(":");
                int from = Integer.parseInt(parts[1]) - 1;
                int to = Integer.parseInt(parts[2]) - 1;
                for (int i = Math.max(0, from); i <= Math.min(totalPages - 1, to); i++) pagesToSign[i] = true;
            }

            PDImageXObject pdImage = LosslessFactory.createFromImage(document, sigImage);

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                if (!pagesToSign[pageIndex]) continue;

                PDPage page = document.getPage(pageIndex);
                PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true);

                float pageW = page.getMediaBox().getWidth();
                float pageH = page.getMediaBox().getHeight();

                float scale = sigSize / 100f;
                float baseWidth = 150f * scale;
                float ratio = sigImage.getHeight() / (float) sigImage.getWidth();
                float sigW = baseWidth;
                float sigH = baseWidth * ratio;

                float x, y;
                switch (position) {
                    case "top-left": x = 30; y = pageH - sigH - 30; break;
                    case "top-center": x = (pageW - sigW)/2; y = pageH - sigH - 30; break;
                    case "top-right": x = pageW - sigW - 30; y = pageH - sigH - 30; break;
                    case "mid-left": x = 30; y = (pageH - sigH)/2; break;
                    case "mid-right": x = pageW - sigW - 30; y = (pageH - sigH)/2; break;
                    case "center": x = (pageW - sigW)/2; y = (pageH - sigH)/2; break;
                    case "bottom-left": x = 30; y = 30; break;
                    case "bottom-right": x = pageW - sigW - 30; y = 30; break;
                    case "bottom-center":
                    default: x = (pageW - sigW)/2; y = 30; break;
                }

                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(opacity / 100f);
                gs.setAlphaSourceFlag(true);
                cs.setGraphicsStateParameters(gs);

                cs.drawImage(pdImage, x, y, sigW, sigH);
                cs.close();
            }

            document.save(output);
        }
        return output;
    }

    // ── PDF TO WORD ────────────────────────────
    // Uses the SAME resolved LibreOffice path as WordService's Word → PDF
    // conversion (via WordService.getSofficePath()) — this fixes the
    // Windows "'C:\Program' is not recognized..." error, which happens
    // when a path containing spaces gets passed as a single string
    // instead of a proper array element to ProcessBuilder.
    // NOTE: LibreOffice's PDF import filter reconstructs text/layout as
    // best it can, so results on heavily-designed or scanned PDFs won't
    // be as clean as the Word → PDF direction. Simple/text-based PDFs
    // convert well.
    public File convertPdfToWord(MultipartFile file) throws IOException {
        String sofficePath = WordService.getSofficePath();

        File sofficeBin = new File(sofficePath);
        if (sofficeBin.isAbsolute() && !sofficeBin.exists()) {
            throw new IOException(
                    "LibreOffice not found at: " + sofficePath +
                    ". Please install LibreOffice or set the SOFFICE_PATH env variable.");
        }

        File workDir = new File(System.getProperty("java.io.tmpdir"),
                "pdf2word_" + UUID.randomUUID());
        if (!workDir.mkdirs()) {
            throw new IOException("Could not create temp working directory");
        }

        File inputPdf = new File(workDir, "input.pdf");
        file.transferTo(inputPdf);

        try {
            // Unique user profile dir, same reasoning as WordService —
            // prevents Windows lock conflicts on parallel requests.
            File userProfile = new File(workDir, "lo-profile");
            userProfile.mkdirs();
            String profileUri = "file:///" + userProfile.getAbsolutePath().replace("\\", "/");

            // IMPORTANT: each argument is its own array element.
            // Never build this as a single concatenated string —
            // that's what breaks on Windows paths with spaces.
            // IMPORTANT: without --infilter, LibreOffice opens a PDF
            // through its Draw component by default, which cannot be
            // exported to "MS Word 2007 XML" (a Writer-only filter) —
            // that mismatch is what causes the 0xc10 write error.
            // Forcing writer_pdf_import makes LO import the PDF as an
            // editable Writer document instead, matching the docx export.
            List<String> cmd = Arrays.asList(
                    sofficePath,
                    "--headless",
                    "--norestore",
                    "--nofirststartwizard",
                    "-env:UserInstallation=" + profileUri,
                    "--infilter=writer_pdf_import",
                    "--convert-to", "docx:MS Word 2007 XML",
                    "--outdir", workDir.getAbsolutePath(),
                    inputPdf.getAbsolutePath()
            );

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
                pb.environment().putIfAbsent("HOME", System.getProperty("user.home", "/tmp"));
            }

            Process process = pb.start();
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes());
            }

            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("PDF to Word conversion timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("LibreOffice conversion failed: " + output);
            }

            File convertedDocx = new File(workDir, "input.docx");
            if (!convertedDocx.exists()) {
                throw new IOException("Conversion did not produce an output file: " + output);
            }

            File finalOutput = File.createTempFile("pdf_to_word_", ".docx",
                    new File(System.getProperty("java.io.tmpdir")));
            try (InputStream in = new FileInputStream(convertedDocx);
                 FileOutputStream out = new FileOutputStream(finalOutput)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return finalOutput;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion interrupted", e);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}