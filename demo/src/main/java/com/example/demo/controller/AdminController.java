package com.example.demo.controller;

import com.example.demo.dto.AdminDashboardDTO;
import com.example.demo.model.PageVisit;
import com.example.demo.model.User;
import com.example.demo.repository.FileHistoryRepository;
import com.example.demo.repository.LoginHistoryRepository;
import com.example.demo.repository.PageVisitRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FileHistoryRepository fileHistoryRepo;
    private final LoginHistoryRepository loginHistoryRepo;
    private final UserRepository userRepo;
    private final PageVisitRepository pageVisitRepo;

    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            LocalDateTime startOfDay =
                LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay =
                LocalDate.now().atTime(23, 59, 59);

            // ── TODAY ──────────────────────────
            long loginsTodayCount = 0;
            long loginsTodayUsers = 0;
            long filesTodayCount  = 0;
            long filesTodaySuccess = 0;
            long newUsersTodayCount = 0;

            try {
                loginsTodayCount = loginHistoryRepo
                    .countByLoggedInAtBetween(
                        startOfDay, endOfDay);
            } catch (Exception e) {
                System.err.println(
                    "loginsTodayCount error: " + e.getMessage());
            }

            try {
                loginsTodayUsers = loginHistoryRepo
                    .countDistinctUserIdByLoggedInAtBetween(
                        startOfDay, endOfDay);
            } catch (Exception e) {
                System.err.println(
                    "loginsTodayUsers error: " + e.getMessage());
            }

            try {
                filesTodaySuccess = fileHistoryRepo
                    .countByStatusAndProcessedAtBetween(
                        "success", startOfDay, endOfDay);
                long filesTodayFailed = fileHistoryRepo
                    .countByStatusAndProcessedAtBetween(
                        "failed", startOfDay, endOfDay);
                filesTodayCount =
                    filesTodaySuccess + filesTodayFailed;
            } catch (Exception e) {
                System.err.println(
                    "filesToday error: " + e.getMessage());
            }

            try {
                newUsersTodayCount = userRepo
                    .countByCreatedAtBetween(
                        startOfDay, endOfDay);
            } catch (Exception e) {
                System.err.println(
                    "newUsers error: " + e.getMessage());
            }

            stats.put("loginsTodayCount",   loginsTodayCount);
            stats.put("loginsTodayUsers",   loginsTodayUsers);
            stats.put("filesTodayCount",    filesTodayCount);
            stats.put("filesTodaySuccess",  filesTodaySuccess);
            stats.put("newUsersTodayCount", newUsersTodayCount);

            // ── ALL TIME ───────────────────────
            long totalUsers  = 0;
            long totalFiles  = 0;
            long totalPdf    = 0;
            long totalImage  = 0;
            long totalAudio  = 0;
            long totalVideo  = 0;

            try { totalUsers = userRepo.count(); }
            catch (Exception e) {
                System.err.println(
                    "totalUsers error: " + e.getMessage());
            }

            try { totalFiles = fileHistoryRepo.count(); }
            catch (Exception e) {
                System.err.println(
                    "totalFiles error: " + e.getMessage());
            }

            try {
                totalPdf   = fileHistoryRepo.countByFileType("pdf");
                totalImage = fileHistoryRepo.countByFileType("image");
                totalAudio = fileHistoryRepo.countByFileType("audio");
                totalVideo = fileHistoryRepo.countByFileType("video");
            } catch (Exception e) {
                System.err.println(
                    "fileType count error: " + e.getMessage());
            }

            stats.put("totalUsersCount", totalUsers);
            stats.put("totalFilesCount", totalFiles);
            stats.put("totalPdfCount",   totalPdf);
            stats.put("totalImageCount", totalImage);
            stats.put("totalAudioCount", totalAudio);
            stats.put("totalVideoCount", totalVideo);

            // ── TOOL USAGE TODAY ───────────────
            List<Map<String, Object>> toolUsage =
                new ArrayList<>();
            try {
                List<Object[]> rows =
                    fileHistoryRepo.findTopToolsToday(
                        startOfDay, endOfDay);
                Map<String, String> iconMap = new HashMap<>();
                iconMap.put("Compress PDF",
                    "bi-file-earmark-zip-fill");
                iconMap.put("Crop Image",      "bi-crop");
                iconMap.put("Resize Image",
                    "bi-arrows-fullscreen");
                iconMap.put("Convert Image",
                    "bi-arrow-left-right");
                iconMap.put("Convert PDF",
                    "bi-arrow-left-right");
                iconMap.put("Trim Audio",      "bi-scissors");
                iconMap.put("Compress Audio",
                    "bi-file-earmark-zip");
                iconMap.put("Compress Video",
                    "bi-file-earmark-zip");
                iconMap.put("Trim Video",      "bi-scissors");
                iconMap.put("Add Watermark",   "bi-droplet-fill");
                iconMap.put("Remove Background","bi-eraser-fill");
                iconMap.put("Lock PDF",        "bi-lock-fill");
                iconMap.put("Unlock PDF",      "bi-unlock-fill");
                iconMap.put("Split PDF",       "bi-scissors");
                iconMap.put("Merge PDFs",      "bi-intersect");
                iconMap.put("Add Signature",   "bi-pen-fill");

                for (Object[] row : rows) {
                    Map<String, Object> t = new HashMap<>();
                    String name  = (String) row[0];
                    Long   count = (Long)   row[1];
                    t.put("name",  name);
                    t.put("count", count);
                    t.put("type",  detectType(name));
                    t.put("icon",  iconMap.getOrDefault(
                        name, "bi-file-earmark"));
                    toolUsage.add(t);
                }
            } catch (Exception e) {
                System.err.println(
                    "toolUsage error: " + e.getMessage());
            }
            stats.put("toolUsage", toolUsage);

            // ── LOCATIONS ──────────────────────
            List<Map<String, Object>> locations =
                new ArrayList<>();
            try {
                List<Object[]> rows =
                    loginHistoryRepo.findLoginsByCountry();
                Map<String, String> flags = new HashMap<>();
                flags.put("India",          "🇮🇳");
                flags.put("United States",  "🇺🇸");
                flags.put("United Kingdom", "🇬🇧");
                flags.put("Germany",        "🇩🇪");
                flags.put("Singapore",      "🇸🇬");

                int limit = 0;
                for (Object[] row : rows) {
                    if (limit++ >= 5) break;
                    Map<String, Object> l = new HashMap<>();
                    String country = (String) row[0];
                    l.put("country", country);
                    l.put("city",    "—");
                    l.put("flag",    flags.getOrDefault(
                        country, "🌍"));
                    l.put("count",   (Long) row[1]);
                    locations.add(l);
                }
            } catch (Exception e) {
                System.err.println(
                    "locations error: " + e.getMessage());
            }
            stats.put("locations", locations);

            // ── RECENT LOGINS ──────────────────
            List<Map<String, Object>> recentLogins =
                new ArrayList<>();
            try {
                var logins = loginHistoryRepo
                    .findTop10ByLoggedInAtBetweenOrderByLoggedInAtDesc(
                        startOfDay, endOfDay);
                for (var l : logins) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("name",     l.getUserName());
                    r.put("email",    l.getUserEmail());
                    r.put("time",
                        l.getLoggedInAt().toLocalTime()
                            .toString().substring(0, 5));
                    r.put("location",
                        l.getCity() + ", " + l.getCountry());
                    r.put("status",   l.getStatus());
                    recentLogins.add(r);
                }
            } catch (Exception e) {
                System.err.println(
                    "recentLogins error: " + e.getMessage());
            }
            stats.put("recentLogins", recentLogins);

            // ── RECENT FILES ───────────────────
            List<Map<String, Object>> recentFiles =
                new ArrayList<>();
            try {
                var files = fileHistoryRepo
                    .findTop10ByProcessedAtBetweenOrderByProcessedAtDesc(
                        startOfDay, endOfDay);
                for (var f : files) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("userName", f.getUserName());
                    r.put("toolUsed", f.getToolUsed());
                    r.put("fileType", f.getFileType());
                    r.put("time",
                        f.getProcessedAt().toLocalTime()
                            .toString().substring(0, 5));
                    r.put("status",   f.getStatus());
                    recentFiles.add(r);
                }
            } catch (Exception e) {
                System.err.println(
                    "recentFiles error: " + e.getMessage());
            }
            stats.put("recentFiles", recentFiles);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/system/status")
    public ResponseEntity<Map<String, Object>> systemStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            userRepo.count();
            status.put("dbConnected", true);
        } catch (Exception e) {
            status.put("dbConnected", false);
        }
        try {
            Process p = new ProcessBuilder(
                "ffmpeg", "-version").start();
            status.put("ffmpegAvailable",
                p.waitFor() == 0);
        } catch (Exception e) {
            status.put("ffmpegAvailable", false);
        }
        return ResponseEntity.ok(status);
    }


    // ════════════════════════════════════════
    // NEW — USERS PAGE
    // ════════════════════════════════════════

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        try {
            List<User> users =
                userRepo.findAllByOrderByCreatedAtDesc();

            List<Map<String, Object>> userList =
                new ArrayList<>();

            for (User u : users) {
                Map<String, Object> m = new HashMap<>();
                m.put("id",        u.getId());
                m.put("firstName", u.getFirstName());
                m.put("lastName",  u.getLastName());
                m.put("email",     u.getEmail());
                m.put("role",      u.getRole());
                m.put("active",    u.isActive());
                m.put("createdAt", u.getCreatedAt());
                userList.add(m);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("users", userList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/toggle-status")
    public ResponseEntity<Map<String, Object>>
            toggleUserStatus(@PathVariable Long id) {
        try {
            User user = userRepo.findById(id)
                .orElseThrow(() ->
                    new RuntimeException("User not found"));

            user.setActive(!user.isActive());
            userRepo.save(user);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "active",  user.isActive()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", e.getMessage()
                ));
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>>
            deleteUser(@PathVariable Long id) {
        try {
            if (!userRepo.existsById(id)) {
                return ResponseEntity.notFound().build();
            }
            userRepo.deleteById(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User deleted"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", e.getMessage()
                ));
        }
    }

    // ════════════════════════════════════════
    // NEW — LOGS PAGE
    // ════════════════════════════════════════

    @GetMapping("/logs/files")
    public ResponseEntity<Map<String, Object>>
            getFileLogs() {
        try {
            var files = fileHistoryRepo
                .findTop100ByOrderByProcessedAtDesc();

            List<Map<String, Object>> fileList =
                new ArrayList<>();

            for (var f : files) {
                Map<String, Object> m = new HashMap<>();
                m.put("userName",    f.getUserName());
                m.put("userEmail",   f.getUserEmail());
                m.put("toolUsed",    f.getToolUsed());
                m.put("fileType",    f.getFileType());
                m.put("fileName",    f.getFileName());
                m.put("fileSize",    f.getFileSize());
                m.put("status",      f.getStatus());
                m.put("processedAt", f.getProcessedAt());
                fileList.add(m);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("files", fileList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/logs/logins")
    public ResponseEntity<Map<String, Object>>
            getLoginLogs() {
        try {
            var logins = loginHistoryRepo
                .findTop100ByOrderByLoggedInAtDesc();

            List<Map<String, Object>> loginList =
                new ArrayList<>();

            for (var l : logins) {
                Map<String, Object> m = new HashMap<>();
                m.put("userName",   l.getUserName());
                m.put("userEmail",  l.getUserEmail());
                m.put("city",       l.getCity());
                m.put("country",    l.getCountry());
                m.put("ipAddress",  l.getIpAddress());
                m.put("status",     l.getStatus());
                m.put("loggedInAt", l.getLoggedInAt());
                loginList.add(m);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("logins", loginList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════
    // NEW — SETTINGS PAGE
    // ════════════════════════════════════════

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>>
            updateProfile(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "success", false,
                        "message", "Name cannot be empty"
                    ));
            }

            // Split name into first/last
            String[] parts = name.trim().split(" ", 2);
            String firstName = parts[0];
            String lastName  = parts.length > 1
                ? parts[1] : "";

            // NOTE: In production, get the actual logged-in
            // admin's email from the JWT token.
            // For now this is a placeholder response.

            return ResponseEntity.ok(Map.of(
                "success",   true,
                "message",   "Profile updated",
                "firstName", firstName,
                "lastName",  lastName
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", e.getMessage()
                ));
        }
    }

    private String detectType(String toolName) {
        if (toolName == null) return "pdf";
        String t = toolName.toLowerCase();
        if (t.contains("image") || t.contains("crop")
            || t.contains("resize") || t.contains("watermark")
            || t.contains("background"))
            return "image";
        if (t.contains("audio")) return "audio";
        if (t.contains("video")) return "video";
        return "pdf";
    }
  // ════════════════════════════════════════
// NEW — VISITOR LOGS PAGE
// ════════════════════════════════════════
@GetMapping("/visitors")
public ResponseEntity<Map<String, Object>>
        getVisitorStats(
        @RequestParam(value = "range",
            defaultValue = "today") String range) {
    try {
        LocalDateTime end   = LocalDateTime.now();
        LocalDateTime start;

        switch (range) {
            case "today":
                start = LocalDate.now().atStartOfDay();
                break;
            case "1m":
                start = end.minusMonths(1);
                break;
            case "1y":
                start = end.minusYears(1);
                break;
            case "7d":
            default:
                start = end.minusDays(7);
                break;
        }

        Map<String, Object> response = new HashMap<>();

        // ── UNIQUE visitor count (fixes the "34" bug) ──
        long uniqueVisitors = pageVisitRepo
            .countDistinctVisitorsBetween(start, end);
        response.put("totalVisits", uniqueVisitors);

        // Table shows ONE row per unique person
        List<PageVisit> visits = pageVisitRepo
            .findDistinctVisitorsBetween(start, end);

        List<Map<String, Object>> visitList = new ArrayList<>();
        int num = 1;
        for (PageVisit v : visits) {
            Map<String, Object> m = new HashMap<>();
            m.put("num",        num++);
            m.put("ipAddress",  v.getIpAddress());
            m.put("location",
                (v.getCity() != null &&
                 !v.getCity().equals("Unknown")
                 ? v.getCity() + ", " : "")
                + (v.getCountry() != null
                   ? v.getCountry() : "Unknown"));
            m.put("deviceType", v.getDeviceType());
            m.put("visitedAt",  v.getVisitedAt());
            visitList.add(m);
        }
        response.put("visits", visitList);

        // Country-wise UNIQUE count
        List<Map<String, Object>> countryCounts = new ArrayList<>();
        try {
            List<Object[]> rows = pageVisitRepo
                .countDistinctByCountry(start, end);
            for (Object[] row : rows) {
                Map<String, Object> c = new HashMap<>();
                c.put("country", row[0]);
                c.put("count",   row[1]);
                countryCounts.add(c);
            }
        } catch (Exception e) {
            System.err.println(
                "country count error: " + e.getMessage());
        }
        response.put("countryCounts", countryCounts);

        return ResponseEntity.ok(response);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError()
            .body(Map.of("error", e.getMessage()));
    }
}

}