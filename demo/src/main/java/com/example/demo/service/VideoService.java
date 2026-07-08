package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Service
public class VideoService {

    // ✅ "ffmpeg" works on Render (Linux) — no hardcoded Windows path
    private static final String FFMPEG = "ffmpeg";

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE MULTIPART FILE → TEMP FILE (streaming — large file safe)
    // ─────────────────────────────────────────────────────────────────────────
    public File saveToTempFile(MultipartFile file) throws IOException {

        String ext = getExtension(file);

        // ✅ /tmp is always writable on Render
        File temp = File.createTempFile(
                "input_" + System.currentTimeMillis(), ext,
                new File(System.getProperty("java.io.tmpdir")));

        try (InputStream in = file.getInputStream();
                OutputStream out = new FileOutputStream(temp)) {

            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return temp;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMPRESS VIDEO
    // ─────────────────────────────────────────────────────────────────────────
    public File compressVideo(File input,
            int crf,
            String resolution,
            String format) throws Exception {

        if (crf <= 0)
            crf = 30;
        if (resolution == null || resolution.isEmpty())
            resolution = "480";
        if (format == null || format.isEmpty())
            format = "mp4";

        File output = File.createTempFile(
                "output_" + System.currentTimeMillis(), "." + format,
                new File(System.getProperty("java.io.tmpdir")));

        String scale = resolution.equals("original")
                ? "scale=iw:ih"
                : "scale=-2:" + resolution;

        List<String> command = Arrays.asList(
                FFMPEG,
                "-y",
                "-i", input.getAbsolutePath(),
                // ✅ Limit threads to reduce RAM usage
                "-threads", "1",
                "-vf", scale,
                "-c:v", "libx264",
                "-crf", String.valueOf(crf),
                "-preset", "veryfast",
                // ✅ Reduce buffer size to save RAM
                "-bufsize", "500k",
                "-maxrate", "500k",
                "-c:a", "aac",
                "-b:a", "64k",
                output.getAbsolutePath());

        runFFmpeg(command);

        // If compression gave no benefit → return original
        if (output.length() >= input.length()) {
            output.delete();
            return input;
        }

        return output;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXTRACT AUDIO
    // ✅ Accepts File (not MultipartFile) — stream already saved by controller
    // ─────────────────────────────────────────────────────────────────────────
    public File extractAudio(File input,
            String format,
            String bitrate,
            String sampleRate,
            String channels) throws Exception {

        File output = File.createTempFile(
                "audio_" + System.currentTimeMillis(), "." + format,
                new File(System.getProperty("java.io.tmpdir")));

        List<String> cmd = Arrays.asList(
                FFMPEG,
                "-y",
                "-i", input.getAbsolutePath(),
                "-vn",
                "-acodec", "libmp3lame",
                "-ab", bitrate + "k",
                "-ar", sampleRate,
                "-ac", channels,
                output.getAbsolutePath());

        runFFmpeg(cmd);

        // ✅ Do NOT delete input here — controller cleans up after streaming
        return output;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRIM VIDEO
    // ✅ Accepts File (not MultipartFile) — stream already saved by controller
    // ─────────────────────────────────────────────────────────────────────────
    public File trimVideo(File input,
            double start,
            double end) throws Exception {

        File output = File.createTempFile(
                "trim_" + System.currentTimeMillis(), ".mp4",
                new File(System.getProperty("java.io.tmpdir")));

        List<String> cmd = Arrays.asList(
                FFMPEG,
                "-y",
                "-i", input.getAbsolutePath(),
                "-ss", String.valueOf(start),
                "-to", String.valueOf(end),
                "-c", "copy",
                output.getAbsolutePath());

        runFFmpeg(cmd);

        // ✅ Do NOT delete input here — controller cleans up after streaming
        return output;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RUN FFMPEG
    // ─────────────────────────────────────────────────────────────────────────
    private void runFFmpeg(List<String> command) throws Exception {

        System.out.println("===== FFMPEG COMMAND =====");
        System.out.println(String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("FFMPEG: " + line);
            }
        }
         // ✅ Timeout — kill FFMPEG if takes more than 5 minutes
    boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
    if (!finished) {
        process.destroyForcibly();
        throw new RuntimeException("Video processing timed out. Try a shorter or smaller video.");
    }

        int exitCode = process.waitFor();
        System.out.println("===== EXIT CODE: " + exitCode + " =====");

        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code: " + exitCode);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private String getExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf("."));
        }
        return ".mp4";
    }
}