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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.awt.Graphics2D;

@Service
public class PdfService {

    // ── MERGE PDFs ────────────────────────────
    public byte[] mergePdfs(MultipartFile[] files) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

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

        merger.setDestinationStream(outputStream);
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
        return outputStream.toByteArray();
    }

    // ── LOCK PDF ──────────────────────────────
    // public byte[] lockPdf(MultipartFile file, String password) throws IOException {
    //     try (InputStream is = file.getInputStream();
    //          PDDocument document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly())) {

    //         AccessPermission ap = new AccessPermission();
    //         ap.setCanPrint(false);
    //         ap.setCanModify(false);
    //         ap.setCanExtractContent(false);

    //         StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, ap);
    //         policy.setEncryptionKeyLength(128);
    //         document.protect(policy);

    //         ByteArrayOutputStream out = new ByteArrayOutputStream();
    //         document.save(out);
    //         return out.toByteArray();
    //     }
    // }

    // // ── UNLOCK PDF ────────────────────────────
    // public byte[] unlockPdf(MultipartFile file, String password) throws IOException {
    //     try (InputStream is = file.getInputStream();
    //          PDDocument document = PDDocument.load(is, password, MemoryUsageSetting.setupTempFileOnly())) {

    //         document.setAllSecurityToBeRemoved(true);
    //         ByteArrayOutputStream out = new ByteArrayOutputStream();
    //         document.save(out);
    //         return out.toByteArray();
    //     }
    // }
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
        // ✅ Owner controls permissions — correctly mapped
        ap.setCanPrint(allowPrint);
        ap.setCanExtractContent(allowCopy);
        ap.setCanModify(allowModify);
        ap.setCanModifyAnnotations(allowAnnotate);
        ap.setCanPrintDegraded(allowPrint);
        ap.setCanExtractForAccessibility(allowCopy);

        // ✅ Owner = controls permissions
        // ✅ User  = open/view only
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
            // ✅ Try loading without password first — detect unprotected PDFs
            document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly());
            if (!document.isEncrypted()) {
                document.close();
                // ✅ Return 400 so frontend can show "not encrypted" message
                throw new IllegalArgumentException("PDF_NOT_ENCRYPTED");
            }
            document.close();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // PDF is encrypted — expected, continue
        }

        // ✅ Now load with password
        try (InputStream is2 = file.getInputStream();
             PDDocument doc = PDDocument.load(is2, password,
                     MemoryUsageSetting.setupTempFileOnly())) {
            doc.setAllSecurityToBeRemoved(true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            // Wrong password
            throw new RuntimeException("WRONG_PASSWORD", e);
        }
    }
}

    // ── SPLIT PDF ─────────────────────────────
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
    public byte[] splitPdfEveryNAsZip(MultipartFile file, int n) throws IOException {
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly())) {

            int totalPages = document.getNumberOfPages();
            ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
                for (int i = 0; i < totalPages; i += n) {
                    try (PDDocument part = new PDDocument()) {
                        for (int j = i; j < Math.min(i + n, totalPages); j++) {
                            part.addPage(document.getPage(j));
                        }
                        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
                        part.save(pdfOut);
                        ZipEntry entry = new ZipEntry("part_" + (i / n + 1) + ".pdf");
                        zos.putNextEntry(entry);
                        zos.write(pdfOut.toByteArray());
                        zos.closeEntry();
                    }
                }
            }
            return zipOut.toByteArray();
        }
    }

    // ── EXTRACT PDF PAGES BY RANGE ────────────
    public byte[] extractPdfPagesByRange(MultipartFile file, String pageRange) throws IOException {
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
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            extracted.save(out);
            return out.toByteArray();
        }
    }

    // ── COMPRESS PDF ──────────────────────────
    public byte[] compressPdf(MultipartFile file, int quality) throws IOException {

    byte[] original = file.getBytes(); // ✅ keep original
    float imageQuality = Math.max(0.4f, Math.min(quality / 100f, 0.8f));

    try (InputStream is = new ByteArrayInputStream(original);
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
                if (image.getWidth() < 500 || image.getHeight() < 500) continue;
                if (image.getStream().getLength() < 50_000) continue;

                BufferedImage buffered;
                try { buffered = image.getImage(); }
                catch (Exception e) { continue; }
                if (buffered == null) continue;

                try {
                    int newWidth  = buffered.getWidth()  / 2;
                    int newHeight = buffered.getHeight() / 2;

                    BufferedImage resized = new BufferedImage(
                            newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = resized.createGraphics();
                    g.drawImage(buffered, 0, 0, newWidth, newHeight, null);
                    g.dispose();
                    buffered.flush();

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

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        byte[] compressed = out.toByteArray();

        // ✅ return original if compression gave no benefit
        return compressed.length < original.length ? compressed : original;
    }
}


    // ── ADD SIGNATURE ─────────────────────────
    public byte[] addSignature(MultipartFile pdfFile,
                               MultipartFile signatureFile,
                               String position,
                               String pageRange,
                               float sigSize,
                               float opacity) throws IOException {

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

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}