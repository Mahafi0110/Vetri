package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class VideoService {

    // "ffmpeg" works on Render (Linux) — no hardcoded Windows path
    private static final String FFMPEG = "ffmpeg";
    // ffprobe is installed together with ffmpeg
    private static final String FFPROBE = "ffprobe";

    // Formats the Trim page offers. Anything else falls back to mp4.
    private static final Set<String> ALLOWED_VIDEO_FORMATS =
            new HashSet<>(Arrays.asList("mp4", "avi", "mov", "mkv", "webm"));

    // Options the Extract Audio page offers. Anything else falls back to a default.
    private static final Set<String> ALLOWED_AUDIO_FORMATS =
            new HashSet<>(Arrays.asList("mp3", "aac", "wav", "ogg", "m4a", "flac"));
    private static final Set<String> ALLOWED_BITRATES =
            new HashSet<>(Arrays.asList("64", "96", "128", "192", "256", "320"));
    private static final Set<String> ALLOWED_SAMPLE_RATES =
            new HashSet<>(Arrays.asList("16000", "22050", "44100", "48000"));
    private static final Set<String> ALLOWED_CHANNELS =
            new HashSet<>(Arrays.asList("1", "2"));

    /** Thrown when the uploaded video has no sound at all. */
    public static class NoAudioTrackException extends RuntimeException {
        public NoAudioTrackException() {
            super("This video has no audio track.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE MULTIPART FILE → TEMP FILE (streaming — large file safe)
    // ─────────────────────────────────────────────────────────────────────────
    public File saveToTempFile(MultipartFile file) throws IOException {

        String ext = getExtension(file);

        // /tmp is always writable on Render
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
    // COMPRESS VIDEO (unchanged, except the format is now checked)
    // ─────────────────────────────────────────────────────────────────────────
    public File compressVideo(File input,
            int crf,
            String resolution,
            String format) throws Exception {

        if (crf <= 0)
            crf = 30;
        if (resolution == null || resolution.isEmpty())
            resolution = "480";
        format = normalizeVideoFormat(format);

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
                // Limit threads to reduce RAM usage
                "-threads", "1",
                "-vf", scale,
                "-c:v", "libx264",
                "-crf", String.valueOf(crf),
                "-preset", "veryfast",
                // Reduce buffer size to save RAM
                "-bufsize", "500k",
                "-maxrate", "500k",
                "-c:a", "aac",
                "-b:a", "64k",
                output.getAbsolutePath());

        try {
            runFFmpeg(command);
        } catch (Exception e) {
            output.delete();
            throw e;
        }

        // If compression gave no benefit → return original
        if (output.length() >= input.length()) {
            output.delete();
            return input;
        }

        return output;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXTRACT AUDIO
    // Now uses the chosen format, bitrate, sample rate and channels.
    //
    // Each format needs its own "codec" (the way the sound is written):
    //   MP3  → libmp3lame        AAC → aac (raw .aac file)
    //   M4A  → aac in an M4A box OGG → libvorbis
    //   WAV  → pcm (no bitrate)  FLAC → flac (no bitrate, lossless)
    // WAV and FLAC are lossless, so the "quality" (bitrate) does not apply.
    // ─────────────────────────────────────────────────────────────────────────
    public File extractAudio(File input,
            String format,
            String bitrate,
            String sampleRate,
            String channels) throws Exception {

        format = normalizeAudioFormat(format);
        bitrate = pick(bitrate, ALLOWED_BITRATES, "192");
        sampleRate = pick(sampleRate, ALLOWED_SAMPLE_RATES, "44100");
        channels = pick(channels, ALLOWED_CHANNELS, "2");

        // Give a clear message instead of a vague FFmpeg error
        if (!hasAudioStream(input)) {
            throw new NoAudioTrackException();
        }

        File output = File.createTempFile(
                "audio_" + System.currentTimeMillis(), "." + format,
                new File(System.getProperty("java.io.tmpdir")));

        List<String> cmd = new ArrayList<>();
        cmd.add(FFMPEG);
        cmd.add("-y");
        cmd.add("-nostdin");
        cmd.add("-i");
        cmd.add(input.getAbsolutePath());
        cmd.add("-vn");               // drop the video
        cmd.add("-map");
        cmd.add("0:a:0");             // use the first audio track

        switch (format) {
            case "mp3":
                cmd.addAll(Arrays.asList("-c:a", "libmp3lame", "-b:a", bitrate + "k"));
                break;
            case "aac":
            case "m4a":
                cmd.addAll(Arrays.asList("-c:a", "aac", "-b:a", bitrate + "k"));
                break;
            case "ogg":
                cmd.addAll(Arrays.asList("-c:a", "libvorbis", "-b:a", bitrate + "k"));
                break;
            case "wav":
                cmd.addAll(Arrays.asList("-c:a", "pcm_s16le"));
                break;
            case "flac":
                cmd.addAll(Arrays.asList("-c:a", "flac"));
                break;
            default:
                cmd.addAll(Arrays.asList("-c:a", "libmp3lame", "-b:a", bitrate + "k"));
        }

        cmd.add("-ar");
        cmd.add(sampleRate);
        cmd.add("-ac");
        cmd.add(channels);
        cmd.add(output.getAbsolutePath());

        try {
            runFFmpeg(cmd);
        } catch (Exception e) {
            output.delete();
            throw e;
        }

        // Do NOT delete input here — controller cleans up after streaming
        return output;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRIM VIDEO
    // Uses the chosen format + quality.
    //
    // How it works:
    //  - "-ss" BEFORE "-i" jumps quickly to the start point.
    //  - "-t" is the length of the clip (end - start).
    //  - The clip is re-encoded, so the cut lands exactly where the user chose.
    //    (The old "-c copy" could only cut on keyframes, so the start/end could
    //    be off by a few seconds, and it could not change format or quality.)
    // ─────────────────────────────────────────────────────────────────────────
    public File trimVideo(File input,
            double start,
            double end,
            String format,
            String quality) throws Exception {

        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        format = normalizeVideoFormat(format);
        String q = (quality == null) ? "medium" : quality.toLowerCase(Locale.ROOT);
        if (!q.equals("high") && !q.equals("medium") && !q.equals("low")) {
            q = "medium";
        }

        File output = File.createTempFile(
                "trim_" + System.currentTimeMillis(), "." + format,
                new File(System.getProperty("java.io.tmpdir")));

        List<String> cmd = new ArrayList<>();
        cmd.add(FFMPEG);
        cmd.add("-y");
        cmd.add("-nostdin");
        cmd.add("-ss");
        cmd.add(String.format(Locale.US, "%.3f", start));
        cmd.add("-i");
        cmd.add(input.getAbsolutePath());
        cmd.add("-t");
        cmd.add(String.format(Locale.US, "%.3f", end - start));

        // Only the first video stream and any audio (the "?" means
        // "don't fail if there is no audio")
        cmd.add("-map");
        cmd.add("0:v:0");
        cmd.add("-map");
        cmd.add("0:a?");

        // Keep RAM low on the small server
        cmd.add("-threads");
        cmd.add("1");

        if (format.equals("webm")) {
            // WEBM only accepts VP8/VP9 video and Opus/Vorbis audio
            cmd.addAll(Arrays.asList(
                    "-c:v", "libvpx-vp9",
                    "-b:v", "0",
                    "-crf", vp9Crf(q),
                    "-deadline", "realtime",
                    "-cpu-used", "8",
                    "-row-mt", "1",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "libopus",
                    "-b:a", audioBitrate(q)));
        } else {
            // MP4 / MOV / MKV / AVI all work with H.264 + AAC
            cmd.addAll(Arrays.asList(
                    "-c:v", "libx264",
                    "-crf", x264Crf(q),
                    "-preset", "veryfast",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    "-b:a", audioBitrate(q)));

            // Lets MP4/MOV start playing before fully downloaded
            if (format.equals("mp4") || format.equals("mov")) {
                cmd.add("-movflags");
                cmd.add("+faststart");
            }
        }

        cmd.add(output.getAbsolutePath());

        try {
            runFFmpeg(cmd);
        } catch (Exception e) {
            output.delete(); // don't leave a half-written file behind
            throw e;
        }

        // Do NOT delete input here — controller cleans up after streaming
        return output;
    }

    // Lower CRF number = better quality + bigger file
    private String x264Crf(String q) {
        switch (q) {
            case "high":
                return "20";
            case "low":
                return "32";
            default:
                return "26";
        }
    }

    private String vp9Crf(String q) {
        switch (q) {
            case "high":
                return "30";
            case "low":
                return "42";
            default:
                return "36";
        }
    }

    private String audioBitrate(String q) {
        switch (q) {
            case "high":
                return "192k";
            case "low":
                return "96k";
            default:
                return "128k";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK IF THE VIDEO HAS SOUND (uses ffprobe)
    // If ffprobe is missing or fails, we return true and let FFmpeg decide,
    // so this check can never block a video that would have worked.
    // ─────────────────────────────────────────────────────────────────────────
    private boolean hasAudioStream(File input) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    FFPROBE,
                    "-v", "error",
                    "-select_streams", "a",
                    "-show_entries", "stream=index",
                    "-of", "csv=p=0",
                    input.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuffer out = new StringBuffer();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        out.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // process ended or was killed
                }
            });
            reader.setDaemon(true);
            reader.start();

            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return true; // could not tell — let FFmpeg try
            }
            reader.join(2000);

            if (process.exitValue() != 0) {
                return true; // could not tell — let FFmpeg try
            }
            return !out.toString().trim().isEmpty();
        } catch (Exception e) {
            return true; // ffprobe not installed etc.
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RUN FFMPEG
    // The timeout really works: FFmpeg's log is read on a separate thread,
    // so waitFor(...) can stop it after 5 minutes.
    // ─────────────────────────────────────────────────────────────────────────
    private void runFFmpeg(List<String> command) throws Exception {

        System.out.println("===== FFMPEG COMMAND =====");
        System.out.println(String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("FFMPEG: " + line);
                }
            } catch (IOException ignored) {
                // process ended or was killed
            }
        });
        logThread.setDaemon(true);
        logThread.start();

        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Video processing timed out. Try a shorter or smaller video.");
        }

        logThread.join(2000);

        int exitCode = process.exitValue();
        System.out.println("===== EXIT CODE: " + exitCode + " =====");

        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code: " + exitCode);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private String normalizeVideoFormat(String format) {
        if (format == null) return "mp4";
        String f = format.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_VIDEO_FORMATS.contains(f) ? f : "mp4";
    }

    /** Public so the controller can use the same safe value for the file name. */
    public String normalizeAudioFormat(String format) {
        if (format == null) return "mp3";
        String f = format.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_AUDIO_FORMATS.contains(f) ? f : "mp3";
    }

    private String pick(String value, Set<String> allowed, String fallback) {
        if (value == null) return fallback;
        String v = value.trim();
        return allowed.contains(v) ? v : fallback;
    }

    private String getExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf("."));
            // Only accept a simple extension like ".mp4"
            if (ext.matches("\\.[A-Za-z0-9]{1,5}")) {
                return ext;
            }
        }
        return ".mp4";
    }
}