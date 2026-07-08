package com.example.demo.config;

import com.example.demo.model.PageVisit;
import com.example.demo.repository.PageVisitRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VisitTrackingFilter implements Filter {

    private final PageVisitRepository pageVisitRepo;

    @Override
    public void doFilter(ServletRequest request,
                          ServletResponse response,
                          FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req = (HttpServletRequest)  request;
        HttpServletResponse res = (HttpServletResponse) response;
        String uri = req.getRequestURI();

        boolean isPage =
            req.getMethod().equals("GET") &&
            !uri.startsWith("/api/") &&
            !uri.startsWith("/css/") &&
            !uri.startsWith("/js/") &&
            !uri.startsWith("/images/") &&
            !uri.startsWith("/admin/") &&
            !uri.contains(".");

        if (isPage) {
            try {
                String visitorId = getOrCreateVisitorId(req, res);

                // Still only 1 row per visitor per day
                // (keeps table size sane, doesn't affect
                //  unique-count logic which uses DISTINCT)
                String today = LocalDate.now().toString();
                String cookieKey = "vetri_visited_" + today;

                if (!hasCookie(req, cookieKey)) {
                    String userAgent = req.getHeader("User-Agent");
                    String ip = getClientIp(req);

                    PageVisit visit = new PageVisit();
                    visit.setVisitorId(visitorId); // ← KEY ADDITION
                    visit.setIpAddress(ip);
                    visit.setUserAgent(userAgent);
                    visit.setDeviceType(detectDevice(userAgent));
                    visit.setCountry("India");
                    visit.setCity("Unknown");
                    pageVisitRepo.save(visit);

                    Cookie marker = new Cookie(cookieKey, "1");
                    marker.setPath("/");
                    marker.setMaxAge(24 * 60 * 60);
                    res.addCookie(marker);
                }
            } catch (Exception e) {
                System.err.println(
                    "Visit log failed: " + e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private String getOrCreateVisitorId(
            HttpServletRequest req,
            HttpServletResponse res) {
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if ("vetri_visitor_id".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        String newId = UUID.randomUUID().toString();
        Cookie idCookie = new Cookie("vetri_visitor_id", newId);
        idCookie.setPath("/");
        idCookie.setMaxAge(365 * 24 * 60 * 60);
        res.addCookie(idCookie);
        return newId;
    }

    private boolean hasCookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return false;
        for (Cookie c : req.getCookies()) {
            if (name.equals(c.getName())) return true;
        }
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() ||
            "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "Unknown";
    }

    private String detectDevice(String ua) {
        if (ua == null) return "Unknown";
        ua = ua.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") ||
            ua.contains("iphone")) return "Mobile";
        if (ua.contains("tablet") || ua.contains("ipad"))
            return "Tablet";
        return "Laptop";
    }
}