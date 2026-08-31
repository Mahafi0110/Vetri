package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@Service
public class AudioService {

    private static final String FFMPEG = "ffmpeg";
    private static final String FFPROBE = "ffprobe";

    // ── CONVERT AUDIO ─────────────────────────
    public File convertAudio(MultipartFile file, String format) throws IOException, InterruptedException {
        if (format == null || format.isEmpty()) format = "mp3";

        File input  = createTemp("input_", getExtension(file));
        File output = createTemp("converted_", "." + format);

        file.transferTo(input);

        List<String> cmd = Arrays.asList(
                FFMPEG, "-y",
                "-threads", "1",
                "-i", input.getAbsolutePath(),
                output.getAbsolutePath()
        );

        runFFmpeg(cmd);
        input.delete();
        return output;
    }

    // ── TRIM AUDIO ────────────────────────────
    public File trimAudio(MultipartFile file, double start, double end) throws IOException, InterruptedException {
        File input  = createTemp("input_", getExtension(file));
        File output = createTemp("trimmed_", ".mp3");

        file.transferTo(input);

        List<String> cmd = Arrays.asList(
                FFMPEG, "-y",
                "-threads", "1",
                "-i", input.getAbsolutePath(),
                "-ss", String.valueOf(start),
                "-to", String.valueOf(end),
                "-c", "copy",
                output.getAbsolutePath()
        );

        runFFmpeg(cmd);
        input.delete();
        return output;
    }

    // ── COMPRESS AUDIO ────────────────────────
    public File compressAudio(MultipartFile file, int bitrate,
            String format, String sampleRate)
            throws IOException, InterruptedException {

        if (bitrate <= 0)                               bitrate    = 128;
        if (format     == null || format.isEmpty())     format     = "mp3";
        if (sampleRate == null || sampleRate.isEmpty()) sampleRate = "44100";

        File input = createTemp("input_", getExtension(file));
        file.transferTo(input);

        // ✅ Don't "compress" upward — if requested bitrate is >= source's
        // actual bitrate, there's nothing to gain and re-encoding can bloat the file.
        int sourceBitrateKbps = estimateSourceBitrateKbps(input);
        if (sourceBitrateKbps > 0 && bitrate >= sourceBitrateKbps) {
            return input; // return the original untouched
        }

        File output = createTemp("compressed_", "." + format);

        List<String> cmd = new ArrayList<>(Arrays.asList(
            FFMPEG, "-y",
            "-threads", "1",
            "-i",      input.getAbsolutePath(),
            "-b:a",    bitrate + "k",
            "-ar",     sampleRate,
            "-acodec", getAudioCodec(format)
        ));

        // ✅ M4A/AAC need an explicit container/muxer format, or ffmpeg can
        // misdetect it from the extension and fail with "Couldn't Compress".
        String containerFormat = getContainerFormat(format);
        if (containerFormat != null) {
            cmd.add("-f");
            cmd.add(containerFormat);
        }
        cmd.add(output.getAbsolutePath());

        runFFmpeg(cmd);
        input.delete();
        return output;
    }

    private String getAudioCodec(String format) {
        if (format == null) return "libmp3lame";
        switch (format.toLowerCase()) {
            case "mp3":  return "libmp3lame";
            case "aac":  return "aac";
            case "ogg":  return "libvorbis";
            case "m4a":  return "aac";
            case "wav":  return "pcm_s16le";
            case "flac": return "flac";
            default:     return "libmp3lame";
        }
    }

    // ✅ Explicit muxer names ffmpeg needs for certain containers
    private String getContainerFormat(String format) {
        if (format == null) return null;
        switch (format.toLowerCase()) {
            case "m4a": return "ipod"; // ffmpeg's muxer name for m4a/mp4-audio
            case "aac": return "adts";
            default:    return null;   // let ffmpeg infer from extension for mp3/wav/ogg/flac
        }
    }

    // ✅ Estimate source bitrate (kbps) using ffprobe, so we know whether
    // compressing at the requested bitrate would actually shrink the file.
    private int estimateSourceBitrateKbps(File input) {
        try {
            List<String> cmd = Arrays.asList(
                FFPROBE, "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=bit_rate",
                "-of", "default=noprint_wrappers=1:nokey=1",
                input.getAbsolutePath()
            );
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                p.waitFor();
                if (line != null && !line.trim().isEmpty() && !line.trim().equals("N/A")) {
                    return Integer.parseInt(line.trim()) / 1000; // bits/sec → kbps
                }
            }
        } catch (Exception e) {
            System.out.println("Could not estimate source bitrate: " + e.getMessage());
        }
        return -1; // unknown — fall back to always compressing
    }

    private File createTemp(String prefix, String suffix) throws IOException {
        return File.createTempFile(
            prefix + System.currentTimeMillis(),
            suffix,
            new File(System.getProperty("java.io.tmpdir"))
        );
    }

    // ── MERGE AUDIO ───────────────────────────
    public File mergeAudio(MultipartFile[] files, String format, int bitrate, double gap) throws IOException, InterruptedException {

        if (format == null || format.isEmpty()) format = "mp3";

        File tempDir = Files.createTempDirectory("audio_merge_").toFile();
        List<File> normalizedFiles = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            File input = File.createTempFile("input_" + i + "_", getExtension(files[i]), new File(System.getProperty("java.io.tmpdir")));
            files[i].transferTo(input);

            File normalized = new File(tempDir, "norm_" + i + ".mp3");
            List<String> normCmd = Arrays.asList(
                    FFMPEG, "-y",
                    "-threads", "1",
                    "-i", input.getAbsolutePath(),
                    "-acodec", "libmp3lame",
                    "-ar", "44100",
                    "-ac", "2",
                    "-ab", bitrate + "k",
                    normalized.getAbsolutePath()
            );
            runFFmpeg(normCmd);
            input.delete();
            normalizedFiles.add(normalized);
        }

        File listFile = new File(tempDir, "list.txt");
        try (PrintWriter pw = new PrintWriter(listFile)) {
            for (int i = 0; i < normalizedFiles.size(); i++) {
                pw.println("file '" + normalizedFiles.get(i).getAbsolutePath().replace("\\", "/") + "'");
                if (gap > 0 && i < normalizedFiles.size() - 1) {
                    File silence = new File(tempDir, "silence_" + i + ".mp3");
                    List<String> silCmd = Arrays.asList(
                            FFMPEG,
                            "-f", "lavfi",
                            "-i", "anullsrc=r=44100:cl=stereo",
                            "-t", String.valueOf(gap),
                            "-y",
                            silence.getAbsolutePath()
                    );
                    runFFmpeg(silCmd);
                    pw.println("file '" + silence.getAbsolutePath().replace("\\", "/") + "'");
                }
            }
        }

        File output = new File(tempDir, "merged_audio." + format);
        List<String> mergeCmd = Arrays.asList(
                FFMPEG,
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.getAbsolutePath(),
                "-b:a", bitrate + "k",
                "-y",
                output.getAbsolutePath()
        );
        runFFmpeg(mergeCmd);

        return output;
    }

    // ── RUN FFMPEG ────────────────────────────
    private void runFFmpeg(List<String> command) throws IOException, InterruptedException {
        System.out.println("===== FFMPEG COMMAND =====");
        System.out.println(String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFmpeg] " + line);
            }
        }

        int exit = process.waitFor();
        System.out.println("===== EXIT CODE: " + exit + " =====");
        if (exit != 0) throw new RuntimeException("FFmpeg failed with exit code " + exit);
    }

    // ── HELPER ───────────────────────────────
    private String getExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf("."));
        }
        return ".mp3";
    }
}