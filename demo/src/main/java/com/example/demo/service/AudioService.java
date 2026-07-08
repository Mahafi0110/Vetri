package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@Service
public class AudioService {

    // ✅ ffmpeg works on Render Linux — no Windows path
    private static final String FFMPEG = "ffmpeg";

    // ── CONVERT AUDIO ─────────────────────────
    public File convertAudio(MultipartFile file, String format) throws IOException, InterruptedException {
        if (format == null || format.isEmpty()) format = "mp3";

        // ✅ Use /tmp explicitly — writable on Render
        File input  = File.createTempFile("input_"  + System.currentTimeMillis(), getExtension(file), new File(System.getProperty("java.io.tmpdir")));
        File output = File.createTempFile("converted_" + System.currentTimeMillis(), "." + format, new File(System.getProperty("java.io.tmpdir")));

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
        File input  = File.createTempFile("input_"   + System.currentTimeMillis(), getExtension(file), new File(System.getProperty("java.io.tmpdir")));
        File output = File.createTempFile("trimmed_" + System.currentTimeMillis(), ".mp3",             new File(System.getProperty("java.io.tmpdir")));

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
    // public File compressAudio(MultipartFile file, int bitrate, String format, String sampleRate) throws IOException, InterruptedException {

    //     if (bitrate <= 0)                              bitrate    = 128;
    //     if (format     == null || format.isEmpty())    format     = "mp3";
    //     if (sampleRate == null || sampleRate.isEmpty()) sampleRate = "44100";

    //     File input  = File.createTempFile("input_"      + System.currentTimeMillis(), getExtension(file), new File(System.getProperty("java.io.tmpdir")));
    //     File output = File.createTempFile("compressed_" + System.currentTimeMillis(), "." + format,       new File(System.getProperty("java.io.tmpdir")));

    //     file.transferTo(input);

    //     List<String> cmd = Arrays.asList(
    //             FFMPEG, "-y",
    //             "-threads", "1",
    //             "-i", input.getAbsolutePath(),
    //             "-b:a", bitrate + "k",
    //             "-ar", sampleRate,
    //             output.getAbsolutePath()
    //     );

    //     runFFmpeg(cmd);

    //     input.delete();

    //     // return original if compression not effective
    //     if (output.length() >= input.length()) return input;
    //     return output;
    // }
    // ── COMPRESS AUDIO ────────────────────────
public File compressAudio(MultipartFile file, int bitrate,
        String format, String sampleRate)
        throws IOException, InterruptedException {

    if (bitrate <= 0)                               bitrate    = 128;
    if (format     == null || format.isEmpty())     format     = "mp3";
    if (sampleRate == null || sampleRate.isEmpty()) sampleRate = "44100";

    File input  = createTemp("input_",      getExtension(file));
    File output = createTemp("compressed_", "." + format);

    file.transferTo(input);

    // ✅ Use correct codec for each format
    List<String> cmd = Arrays.asList(
        FFMPEG, "-y",
        "-threads", "1",
        "-i",      input.getAbsolutePath(),
        "-b:a",    bitrate + "k",
        "-ar",     sampleRate,
        "-acodec", getAudioCodec(format),  // ✅ ADD THIS
        output.getAbsolutePath()
    );

    runFFmpeg(cmd);
    input.delete();
    return output;
}

// ✅ ADD THIS METHOD to AudioService
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

// ✅ ADD THIS HELPER too (used in compressAudio)
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