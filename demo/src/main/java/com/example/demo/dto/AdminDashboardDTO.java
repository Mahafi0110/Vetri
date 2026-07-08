package com.example.demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminDashboardDTO {

    // Today
    private long loginsTodayCount;
    private long loginsTodayUsers;
    private long filesTodayCount;
    private long filesTodaySuccess;
    private long newUsersTodayCount;

    // All time
    private long totalUsersCount;
    private long totalFilesCount;
    private long totalPdfCount;
    private long totalImageCount;
    private long totalAudioCount;
    private long totalVideoCount;

    // Tool usage today
    private List<ToolUsageDTO> toolUsage;

    // Location
    private List<LocationDTO> locations;

    // Recent logins
    private List<RecentLoginDTO> recentLogins;

    // Recent files
    private List<RecentFileDTO> recentFiles;

    // ── Nested DTOs ──

    @Data
    public static class ToolUsageDTO {
        private String name;
        private String type;
        private String icon;
        private long   count;
    }

    @Data
    public static class LocationDTO {
        private String country;
        private String city;
        private String flag;
        private long   count;
    }

    @Data
    public static class RecentLoginDTO {
        private String name;
        private String email;
        private String time;
        private String location;
        private String status;
    }

    @Data
    public static class RecentFileDTO {
        private String userName;
        private String toolUsed;
        private String fileType;
        private String time;
        private String status;
    }
}