package com.example.demo.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class ImageService {

    // ── Inject from application.properties ──────────────────────────────────
    // Set: app.removebg.api-key=YOUR_KEY   (leave blank to use fallback only)
    @Value("${app.removebg.api-key:}")
    private String removeBgApiKey;

    // ── COMPRESS IMAGE ───────────────────────────────────────────────────────
    public byte[] compressImage(MultipartFile file, float quality)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .scale(1.0)
                .outputQuality(quality)
                .outputFormat("jpg")
                .toOutputStream(out);
        return out.toByteArray();
    }

    // ── RESIZE IMAGE ─────────────────────────────────────────────────────────
    public byte[] resizeImage(MultipartFile file, int width, int height)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .size(width, height)
                .keepAspectRatio(false)
                .outputFormat("jpg")
                .toOutputStream(out);
        return out.toByteArray();
    }

    // ── CROP IMAGE ───────────────────────────────────────────────────────────
    public byte[] cropImage(MultipartFile file, int x, int y, int width, int height)
            throws IOException {
        BufferedImage original = ImageIO.read(file.getInputStream());
        int maxX  = Math.min(x + width,  original.getWidth());
        int maxY  = Math.min(y + height, original.getHeight());
        int cropW = maxX - x;
        int cropH = maxY - y;
        BufferedImage cropped = original.getSubimage(x, y, cropW, cropH);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(cropped, "jpg", out);
        return out.toByteArray();
    }

    // ── CONVERT IMAGE ────────────────────────────────────────────────────────
    public byte[] convertImage(MultipartFile file, String format) throws IOException {
        if (format.equalsIgnoreCase("webp")) format = "png"; // Java ImageIO has no WebP encoder

        BufferedImage image = ImageIO.read(file.getInputStream());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // JPG cannot store transparency — flatten to white
        if (format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg")) {
            BufferedImage rgb = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            image = rgb;
        }

        ImageIO.write(image, format.toUpperCase(), baos);
        return baos.toByteArray();
    }

    // ── ADD WATERMARK ────────────────────────────────────────────────────────
    public byte[] addWatermark(MultipartFile file, String text) throws IOException {
        BufferedImage original = ImageIO.read(file.getInputStream());
        Graphics2D g2d = original.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g2d.setColor(Color.GRAY);
        int fontSize = Math.max(24, original.getWidth() / 10);
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (original.getWidth()  - fm.stringWidth(text)) / 2;
        int y =  original.getHeight() / 2;
        g2d.drawString(text, x, y);
        g2d.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(original, "jpg", out);
        return out.toByteArray();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  REMOVE BACKGROUND  — tries remove.bg API first, falls back to
    //  an improved local algorithm if the API key is absent or quota exceeded.
    // ════════════════════════════════════════════════════════════════════════
    public byte[] removeBackground(MultipartFile file,
                                   String bgOption,
                                   String bgColor,
                                   String refinement,
                                   String format) throws IOException {

        byte[] transparentPng;

        // ── Step 1: get a transparent PNG with background removed ────────────
        if (removeBgApiKey != null && !removeBgApiKey.isBlank()) {
            try {
                transparentPng = callRemoveBgApi(file);
            } catch (Exception e) {
                System.err.println("[RemoveBg] API failed (" + e.getMessage() + "), using local fallback.");
                transparentPng = localRemoveBackground(file, refinement);
            }
        } else {
            // No API key — use local algorithm
            transparentPng = localRemoveBackground(file, refinement);
        }

        // ── Step 2: composite the chosen background onto the transparent PNG ─
        return compositeBackground(transparentPng, bgOption, bgColor, format);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  remove.bg REST API  (https://www.remove.bg/api)
    //  Free tier: 50 API calls / month, up to 0.25 MP per call (preview size)
    //  Paid plans: full resolution
    // ────────────────────────────────────────────────────────────────────────
    private byte[] callRemoveBgApi(MultipartFile file) throws Exception {

         // ✅ ADD THIS — tells you in Render logs if API is being called
    System.out.println("[RemoveBg] API key present: " + 
        (removeBgApiKey != null && !removeBgApiKey.isBlank()));
    System.out.println("[RemoveBg] File: " + file.getOriginalFilename() + 
        " size: " + file.getSize());
        String boundary = UUID.randomUUID().toString();

        // Build multipart body manually (Java 11 HttpClient)
        byte[] fileBytes  = file.getBytes();
        String filename   = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "image.png";

        // Multipart parts
        String partHeader = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image_file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + file.getContentType() + "\r\n\r\n";

        String sizeField = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"size\"\r\n\r\n"
                + "auto\r\n";

        String closing = "--" + boundary + "--\r\n";

        // Concatenate body bytes
        byte[] partHeaderBytes = partHeader.getBytes(StandardCharsets.UTF_8);
        byte[] crlf            = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] sizeFieldBytes  = sizeField.getBytes(StandardCharsets.UTF_8);
        byte[] closingBytes    = closing.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
        bodyOut.write(partHeaderBytes);
        bodyOut.write(fileBytes);
        bodyOut.write(crlf);
        bodyOut.write(sizeFieldBytes);
        bodyOut.write(closingBytes);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.remove.bg/v1.0/removebg"))
                .header("X-Api-Key",    removeBgApiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept",       "image/png")
                .POST(BodyPublishers.ofByteArray(bodyOut.toByteArray()))
                .build();

        HttpResponse<byte[]> response = client.send(request, BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("remove.bg API returned " + response.statusCode() + ": " + body);
        }

        return response.body(); // already a transparent PNG
    }

    // ────────────────────────────────────────────────────────────────────────
    //  LOCAL REMOVE BACKGROUND — improved multi-sample edge-aware algorithm
    //
    //  Strategy:
    //  1. Sample background color from all 4 corners + edges
    //  2. Build an alpha mask using color distance with soft edges
    //  3. Apply morphological erosion to clean noise
    //  4. Blur the alpha mask for smooth edges (refinement setting)
    // ────────────────────────────────────────────────────────────────────────
    private byte[] localRemoveBackground(MultipartFile file, String refinement)
            throws IOException {

        BufferedImage src = ImageIO.read(file.getInputStream());

// ✅ Add null check — ImageIO returns null for unsupported formats
if (src == null) {
    // Try reading with a copy of the stream
    try (InputStream is2 = file.getInputStream()) {
        src = ImageIO.read(new javax.imageio.stream.MemoryCacheImageInputStream(is2));
    }
}

if (src == null) {
    throw new IOException("Cannot read image file: " + file.getOriginalFilename() 
        + ". Supported formats: JPG, PNG, BMP, GIF, TIFF");
}
        int W = src.getWidth();
        int H = src.getHeight();

        // ── 1. Sample background colour from border pixels ──────────────────
        int bgR = 0, bgG = 0, bgB = 0, count = 0;
        int sampleStep = Math.max(1, Math.min(W, H) / 40);
        // Top and bottom rows
        for (int x = 0; x < W; x += sampleStep) {
            bgR += red(src.getRGB(x, 0));   bgG += green(src.getRGB(x, 0));   bgB += blue(src.getRGB(x, 0));   count++;
            bgR += red(src.getRGB(x, H-1)); bgG += green(src.getRGB(x, H-1)); bgB += blue(src.getRGB(x, H-1)); count++;
        }
        // Left and right columns
        for (int y = 0; y < H; y += sampleStep) {
            bgR += red(src.getRGB(0, y));   bgG += green(src.getRGB(0, y));   bgB += blue(src.getRGB(0, y));   count++;
            bgR += red(src.getRGB(W-1, y)); bgG += green(src.getRGB(W-1, y)); bgB += blue(src.getRGB(W-1, y)); count++;
        }
        bgR /= count; bgG /= count; bgB /= count;

        // ── 2. Compute alpha mask via colour distance ────────────────────────
        // Tolerance: pixels within innerT distance become fully transparent,
        //            pixels beyond outerT distance stay fully opaque,
        //            between them is a smooth linear gradient (soft edge).
        int innerT;
        int outerT;
        switch (refinement == null ? "balanced" : refinement.toLowerCase()) {
            case "sharp":
                innerT = 25; outerT = 45; break;
            case "smooth":
                innerT = 40; outerT = 90; break;
            default: // balanced
                innerT = 30; outerT = 60; break;
        }

        float[] alpha = new float[W * H]; // 0.0 = transparent, 1.0 = opaque
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb   = src.getRGB(x, y);
                int dist  = colorDist(red(rgb), green(rgb), blue(rgb), bgR, bgG, bgB);
                float a;
                if (dist <= innerT)       a = 0f;
                else if (dist >= outerT)  a = 1f;
                else                      a = (float)(dist - innerT) / (outerT - innerT);
                alpha[y * W + x] = a;
            }
        }

        // ── 3. Flood-fill from all 4 corners so interior background patches
        //       that happen to have the same colour are NOT removed ──────────
        // (Only pixels reachable from the border are candidate background)
        boolean[] reachable = new boolean[W * H];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        // Seed border pixels that are "background-like"
        for (int x = 0; x < W; x++) {
            tryAdd(queue, reachable, alpha, x, 0,   W, H, innerT, outerT, src, bgR, bgG, bgB);
            tryAdd(queue, reachable, alpha, x, H-1, W, H, innerT, outerT, src, bgR, bgG, bgB);
        }
        for (int y = 0; y < H; y++) {
            tryAdd(queue, reachable, alpha, 0,   y, W, H, innerT, outerT, src, bgR, bgG, bgB);
            tryAdd(queue, reachable, alpha, W-1, y, W, H, innerT, outerT, src, bgR, bgG, bgB);
        }
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                tryAdd(queue, reachable, alpha, p[0]+d[0], p[1]+d[1], W, H, innerT, outerT, src, bgR, bgG, bgB);
            }
        }
        // Zero out non-reachable background guesses (they are foreground)
        for (int i = 0; i < W * H; i++) {
            if (!reachable[i]) alpha[i] = 1f;
        }

        // ── 4. Optional: soften the alpha mask (Gaussian-like box blur) ──────
        int blurRadius;
        switch (refinement == null ? "balanced" : refinement.toLowerCase()) {
            case "smooth": blurRadius = 3; break;
            case "sharp":  blurRadius = 0; break;
            default:       blurRadius = 1; break;
        }
        if (blurRadius > 0) {
            alpha = boxBlurAlpha(alpha, W, H, blurRadius);
        }

        // ── 5. Build ARGB result image ────────────────────────────────────────
        BufferedImage result = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int   srcRgb = src.getRGB(x, y);
                float a      = alpha[y * W + x];
                int   ia     = Math.min(255, Math.max(0, Math.round(a * 255)));
                int   argb   = (ia << 24)
                             | (red(srcRgb)   << 16)
                             | (green(srcRgb) <<  8)
                             |  blue(srcRgb);
                result.setRGB(x, y, argb);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(result, "png", out); // always PNG — keeps alpha
        return out.toByteArray();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Composite background onto a transparent PNG
    // ────────────────────────────────────────────────────────────────────────
    private byte[] compositeBackground(byte[] transparentPng,
                                       String bgOption,
                                       String bgColor,
                                       String format) throws IOException {

        BufferedImage src = ImageIO.read(new ByteArrayInputStream(transparentPng));

        // If transparent + PNG: return as-is (already correct)
        if ("transparent".equals(bgOption) && !"jpg".equalsIgnoreCase(format)) {
            if ("png".equalsIgnoreCase(format)) return transparentPng;
            // webp / other: re-encode via ImageIO
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(src, format.equalsIgnoreCase("jpg") ? "jpg" : "png", out);
            return out.toByteArray();
        }

        // For any solid background or JPG (which can't store alpha): composite
        BufferedImage composite = new BufferedImage(src.getWidth(), src.getHeight(),
                "jpg".equalsIgnoreCase(format) ? BufferedImage.TYPE_INT_RGB
                                               : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = composite.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill background
        switch (bgOption == null ? "transparent" : bgOption) {
            case "white":
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, composite.getWidth(), composite.getHeight());
                break;
            case "black":
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, composite.getWidth(), composite.getHeight());
                break;
            case "color":
                try { g.setColor(Color.decode(bgColor)); }
                catch (NumberFormatException e) { g.setColor(Color.WHITE); }
                g.fillRect(0, 0, composite.getWidth(), composite.getHeight());
                break;
            default:
                // transparent — for PNG leave clear; for JPG use white
                if ("jpg".equalsIgnoreCase(format)) {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, composite.getWidth(), composite.getHeight());
                }
                break;
        }

        g.drawImage(src, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String ioFormat = "jpg".equalsIgnoreCase(format) ? "jpg" : "png";
        ImageIO.write(composite, ioFormat, out);
        return out.toByteArray();
    }

    // ─── Flood-fill helper ───────────────────────────────────────────────────
    private void tryAdd(java.util.ArrayDeque<int[]> queue,
                        boolean[] reachable,
                        float[] alpha,
                        int x, int y, int W, int H,
                        int innerT, int outerT,
                        BufferedImage src,
                        int bgR, int bgG, int bgB) {
        if (x < 0 || x >= W || y < 0 || y >= H) return;
        int idx = y * W + x;
        if (reachable[idx]) return;
        // Only flood through pixels that are background-like (alpha < 1)
        if (alpha[idx] >= 1f) return;
        reachable[idx] = true;
        queue.add(new int[]{x, y});
    }

    // ─── Box blur on alpha channel ───────────────────────────────────────────
    private float[] boxBlurAlpha(float[] alpha, int W, int H, int radius) {
        float[] tmp = new float[W * H];
        float   div = 2 * radius + 1;
        // Horizontal pass
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int nx = Math.max(0, Math.min(W-1, x + k));
                    sum += alpha[y * W + nx];
                }
                tmp[y * W + x] = sum / div;
            }
        }
        // Vertical pass
        float[] result = new float[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int ny = Math.max(0, Math.min(H-1, y + k));
                    sum += tmp[ny * W + x];
                }
                result[y * W + x] = sum / div;
            }
        }
        return result;
    }

    // ─── Colour component helpers ────────────────────────────────────────────
    private int red(int rgb)   { return (rgb >> 16) & 0xFF; }
    private int green(int rgb) { return (rgb >>  8) & 0xFF; }
    private int blue(int rgb)  { return  rgb        & 0xFF; }

    private int colorDist(int r1, int g1, int b1, int r2, int g2, int b2) {
        // Weighted Euclidean distance — human eye is most sensitive to green
        double dr = (r1 - r2) * 0.30;
        double dg = (g1 - g2) * 0.59;
        double db = (b1 - b2) * 0.11;
        return (int) Math.sqrt(dr*dr + dg*dg + db*db);
    }
}