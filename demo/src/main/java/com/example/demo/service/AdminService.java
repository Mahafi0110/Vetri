package com.example.demo.service;

import com.example.demo.dto.AdminDashboardDTO;
import com.example.demo.model.FileHistory;
import com.example.demo.model.LoginHistory;
import com.example.demo.repository.FileHistoryRepository;
import com.example.demo.repository.LoginHistoryRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final FileHistoryRepository fileHistoryRepo;
    private final LoginHistoryRepository loginHistoryRepo;
    private final UserRepository userRepo;

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("hh:mm a");

    /* ── Get Dashboard Stats ── */
    public AdminDashboardDTO getDashboardStats() {
        AdminDashboardDTO dto = new AdminDashboardDTO();

        // Today range
        LocalDateTime startOfDay =
            LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay =
            LocalDate.now().atTime(23, 59, 59);

        // ── TODAY STATS ──
        dto.setLoginsTodayCount(
            loginHistoryRepo.countByLoggedInAtBetween(
                startOfDay, endOfDay
            )
        );
        dto.setLoginsTodayUsers(
            loginHistoryRepo
              .countDistinctUserIdByLoggedInAtBetween(
                startOfDay, endOfDay
              )
        );
        dto.setFilesTodayCount(
            fileHistoryRepo.countByStatusAndProcessedAtBetween(
                "success", startOfDay, endOfDay
            ) +
            fileHistoryRepo.countByStatusAndProcessedAtBetween(
                "failed", startOfDay, endOfDay
            )
        );
        dto.setFilesTodaySuccess(
            fileHistoryRepo.countByStatusAndProcessedAtBetween(
                "success", startOfDay, endOfDay
            )
        );

        // New users today
        dto.setNewUsersTodayCount(
            userRepo.countByCreatedAtBetween(
                startOfDay, endOfDay
            )
        );

        // ── ALL TIME STATS ──
        dto.setTotalUsersCount(userRepo.count());
        dto.setTotalFilesCount(fileHistoryRepo.count());
        dto.setTotalPdfCount(
            fileHistoryRepo.countByFileType("pdf")
        );
        dto.setTotalImageCount(
            fileHistoryRepo.countByFileType("image")
        );
        dto.setTotalAudioCount(
            fileHistoryRepo.countByFileType("audio")
        );
        dto.setTotalVideoCount(
            fileHistoryRepo.countByFileType("video")
        );

        // ── TOOL USAGE TODAY ──
        List<Object[]> toolRows =
            fileHistoryRepo.findTopToolsToday(
                startOfDay, endOfDay
            );
        List<AdminDashboardDTO.ToolUsageDTO> toolUsage =
            new ArrayList<>();
        for (Object[] row : toolRows) {
            AdminDashboardDTO.ToolUsageDTO t =
                new AdminDashboardDTO.ToolUsageDTO();
            t.setName((String) row[0]);
            t.setCount((Long) row[1]);
            t.setType(detectType((String) row[0]));
            t.setIcon(detectIcon((String) row[0]));
            toolUsage.add(t);
        }
        dto.setToolUsage(toolUsage);

        // ── LOCATIONS ──
        List<Object[]> locRows =
            loginHistoryRepo.findLoginsByCountry();
        List<AdminDashboardDTO.LocationDTO> locations =
            new ArrayList<>();
        for (Object[] row : locRows) {
            AdminDashboardDTO.LocationDTO l =
                new AdminDashboardDTO.LocationDTO();
            l.setCountry((String) row[0]);
            l.setCity("—");
            l.setFlag(getFlag((String) row[0]));
            l.setCount((Long) row[1]);
            locations.add(l);
            if (locations.size() >= 5) break;
        }
        dto.setLocations(locations);

        // ── RECENT LOGINS ──
        List<LoginHistory> logins =
            loginHistoryRepo
              .findTop10ByLoggedInAtBetweenOrderByLoggedInAtDesc(
                startOfDay, endOfDay
              );
        List<AdminDashboardDTO.RecentLoginDTO> recentLogins =
            new ArrayList<>();
        for (LoginHistory l : logins) {
            AdminDashboardDTO.RecentLoginDTO r =
                new AdminDashboardDTO.RecentLoginDTO();
            r.setName(l.getUserName());
            r.setEmail(l.getUserEmail());
            r.setTime(l.getLoggedInAt().format(TIME_FMT));
            r.setLocation(
                l.getCity() + ", " + l.getCountry()
            );
            r.setStatus(l.getStatus());
            recentLogins.add(r);
        }
        dto.setRecentLogins(recentLogins);

        // ── RECENT FILES ──
        List<FileHistory> files =
            fileHistoryRepo
              .findTop10ByProcessedAtBetweenOrderByProcessedAtDesc(
                startOfDay, endOfDay
              );
        List<AdminDashboardDTO.RecentFileDTO> recentFiles =
            new ArrayList<>();
        for (FileHistory f : files) {
            AdminDashboardDTO.RecentFileDTO r =
                new AdminDashboardDTO.RecentFileDTO();
            r.setUserName(f.getUserName());
            r.setToolUsed(f.getToolUsed());
            r.setFileType(f.getFileType());
            r.setTime(f.getProcessedAt().format(TIME_FMT));
            r.setStatus(f.getStatus());
            recentFiles.add(r);
        }
        dto.setRecentFiles(recentFiles);

        return dto;
    }

    /* ── Detect tool type ── */
    private String detectType(String toolName) {
        if (toolName == null) return "pdf";
        String t = toolName.toLowerCase();
        if (t.contains("image") || t.contains("crop") ||
            t.contains("resize") || t.contains("watermark") ||
            t.contains("background"))
            return "image";
        if (t.contains("audio") || t.contains("music"))
            return "audio";
        if (t.contains("video")) return "video";
        return "pdf";
    }

    /* ── Detect tool icon ── */
    private String detectIcon(String toolName) {
        if (toolName == null) return "bi-file-earmark";
        String t = toolName.toLowerCase();
        if (t.contains("compress"))
            return "bi-file-earmark-zip-fill";
        if (t.contains("crop"))    return "bi-crop";
        if (t.contains("resize"))  return "bi-arrows-fullscreen";
        if (t.contains("convert")) return "bi-arrow-left-right";
        if (t.contains("merge"))   return "bi-intersect";
        if (t.contains("split"))   return "bi-scissors";
        if (t.contains("lock"))    return "bi-lock-fill";
        if (t.contains("unlock"))  return "bi-unlock-fill";
        if (t.contains("trim"))    return "bi-scissors";
        if (t.contains("watermark"))return "bi-droplet-fill";
        if (t.contains("signature"))return "bi-pen-fill";
        if (t.contains("background"))return "bi-eraser-fill";
        return "bi-file-earmark";
    }

    /* ── Country flag ── */
    private String getFlag(String country) {
        if (country == null) return "🌍";
        Map<String, String> flags = new HashMap<>();
        flags.put("India",          "🇮🇳");
        flags.put("United States",  "🇺🇸");
        flags.put("United Kingdom", "🇬🇧");
        flags.put("Germany",        "🇩🇪");
        flags.put("Singapore",      "🇸🇬");
        flags.put("Australia",      "🇦🇺");
        flags.put("Canada",         "🇨🇦");
        flags.put("France",         "🇫🇷");
        flags.put("Japan",          "🇯🇵");
        flags.put("Brazil",         "🇧🇷");
        return flags.getOrDefault(country, "🌍");
    }

    /* ── System Status ── */
    public Map<String, Boolean> getSystemStatus() {
        Map<String, Boolean> status = new HashMap<>();
        // DB check
        try {
            userRepo.count();
            status.put("dbConnected", true);
        } catch (Exception e) {
            status.put("dbConnected", false);
        }
        // FFmpeg check
        try {
            Process p = new ProcessBuilder(
                "ffmpeg", "-version"
            ).start();
            status.put("ffmpegAvailable",
                p.waitFor() == 0);
        } catch (Exception e) {
            status.put("ffmpegAvailable", false);
        }
        return status;
    }

    /* ── Log file processing ── */
    public void logFileProcess(
            Long userId, String userName,
            String userEmail, String toolUsed,
            String fileType, String fileName,
            Long fileSize, String status,
            String location) {
        FileHistory h = new FileHistory();
        h.setUserId(userId);
        h.setUserName(userName);
        h.setUserEmail(userEmail);
        h.setToolUsed(toolUsed);
        h.setFileType(fileType);
        h.setFileName(fileName);
        h.setFileSize(fileSize);
        h.setStatus(status);
        h.setLocation(location);
        fileHistoryRepo.save(h);
    }

    /* ── Log login ── */
    public void logLogin(
            Long userId, String userName,
            String userEmail, String ipAddress,
            String country, String city) {
        LoginHistory h = new LoginHistory();
        h.setUserId(userId);
        h.setUserName(userName);
        h.setUserEmail(userEmail);
        h.setIpAddress(ipAddress);
        h.setCountry(country != null ? country : "Unknown");
        h.setCity(city != null ? city : "Unknown");
        h.setLocation(
            (city != null ? city : "") +
            (country != null ? ", " + country : "")
        );
        h.setStatus("success");
        loginHistoryRepo.save(h);
    }
}