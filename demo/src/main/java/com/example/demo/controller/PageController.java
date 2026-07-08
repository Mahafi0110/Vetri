package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    // ── Helper to extract token ──
    private String extractToken(HttpServletRequest request) {
        // 1. Check Authorization header
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // 2. Check cookie
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("vetri_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    // ── Helper to get user from request ──
    private User getUserFromRequest(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null)
                return null;
            if (!jwtUtil.validateToken(token))
                return null;
            String email = jwtUtil.getEmailFromToken(token);
            return userRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ══════════════════════════════════════════
    // MAIN PAGES
    // ══════════════════════════════════════════

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/index")
    public String indexAlt() {
        return "redirect:/";
    }

    @GetMapping("/all-tools")
    public String allTools() {
        return "all-tools";
    }

    @GetMapping("/file-tools")
    public String fileTools() {
        return "file-tools";
    }

    @GetMapping("/Feature")
    public String feature() {
        return "Feature";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPassword() {
        return "reset-password";
    }

    @GetMapping("/favicon.ico")
    @ResponseBody
    public void favicon() {
    }

    // ══════════════════════════════════════════
    // ADMIN PAGES — role checked manually
    // ══════════════════════════════════════════

    @GetMapping("/admin/dashboard")
    public String adminDashboard(HttpServletRequest request) {
        User user = getUserFromRequest(request);

        // Not logged in
        if (user == null) {
            return "redirect:/login?next=/admin/dashboard";
        }

        // Logged in but not admin
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            return "redirect:/?error=access_denied";
        }

        // All good — show dashboard
        return "admin/dashboard";
    }

    // ══════════════════════════════════════════
    // PDF TOOL PAGES
    // ══════════════════════════════════════════

    @GetMapping("/tool-convert-pdf")
    public String toolConvertPdf() {
        return "tool-convert-pdf";
    }

    @GetMapping("/tool-compress-pdf")
    public String toolCompressPdf() {
        return "tool-compress-pdf";
    }

    @GetMapping("/tool-merge-pdf")
    public String toolMergePdf() {
        return "tool-merge-pdf";
    }

    @GetMapping("/tool-split-pdf")
    public String toolSplitPdf() {
        return "tool-split-pdf";
    }

    @GetMapping("/tool-add-signature")
    public String toolAddSignature() {
        return "tool-add-signature";
    }

    @GetMapping("/tool-lock-pdf")
    public String toolLockPdf() {
        return "tool-lock-pdf";
    }

    @GetMapping("/tool-unlock-pdf")
    public String toolUnlockPdf() {
        return "tool-unlock-pdf";
    }

    // ══════════════════════════════════════════
    // IMAGE TOOL PAGES
    // ══════════════════════════════════════════

    @GetMapping("/tool-convert-image")
    public String toolConvertImage() {
        return "tool-convert-image";
    }

    @GetMapping("/tool-crop-image")
    public String toolCropImage() {
        return "tool-crop-image";
    }

    @GetMapping("/tool-resize-image")
    public String toolResizeImage() {
        return "tool-resize-image";
    }

    @GetMapping("/tool-compress-image")
    public String toolCompressImage() {
        return "tool-compress-image";
    }

    @GetMapping("/tool-watermark-image")
    public String toolWatermarkImage() {
        return "tool-watermark-image";
    }

    @GetMapping("/tool-merge-images")
    public String toolMergeImages() {
        return "tool-merge-images";
    }

    @GetMapping("/tool-remove-background")
    public String toolRemoveBackground() {
        return "tool-remove-background";
    }

    // ══════════════════════════════════════════
    // AUDIO TOOL PAGES
    // ══════════════════════════════════════════

    @GetMapping("/tool-convert-audio")
    public String toolConvertAudio() {
        return "tool-convert-audio";
    }

    @GetMapping("/tool-trim-audio")
    public String toolTrimAudio() {
        return "tool-trim-audio";
    }

    @GetMapping("/tool-merge-audio")
    public String toolMergeAudio() {
        return "tool-merge-audio";
    }

    @GetMapping("/tool-compress-audio")
    public String toolCompressAudio() {
        return "tool-compress-audio";
    }

    @GetMapping("/tool-extract-audio")
    public String toolExtractAudio() {
        return "tool-extract-audio";
    }

    // ══════════════════════════════════════════
    // VIDEO TOOL PAGES
    // ══════════════════════════════════════════

    @GetMapping("/tool-compress-video")
    public String toolCompressVideo() {
        return "tool-compress-video";
    }

    @GetMapping("/tool-trim-video")
    public String toolTrimVideo() {
        return "tool-trim-video";
    }

    @GetMapping("/tool-merge-videos")
    public String toolMergeVideos() {
        return "tool-merge-videos";
    }

    @GetMapping("/tool-extract-audio-video")
    public String toolExtractAudioVideo() {
        return "tool-extract-audio-video";
    }

    @GetMapping("/tool-word-pdf")
    public String wordPdfPage() {
        return "tool-word-pdf";
    }

    @GetMapping("/tool-word-html")
    public String wordHtmlPage() {
        return "tool-word-html";
    }

    @GetMapping("/tool-word-text")
    public String wordTextPage() {
        return "tool-word-text";
    }
    @GetMapping("/admin/users")
public String adminUsers(HttpServletRequest request) {
    User user = getUserFromRequest(request);
    if (user == null) return "redirect:/login";
    if (!"ADMIN".equalsIgnoreCase(user.getRole()))
        return "redirect:/?error=access_denied";
    return "admin/users";
}

@GetMapping("/admin/logs")
public String adminLogs(HttpServletRequest request) {
    User user = getUserFromRequest(request);
    if (user == null) return "redirect:/login";
    if (!"ADMIN".equalsIgnoreCase(user.getRole()))
        return "redirect:/?error=access_denied";
    return "admin/logs";
}

@GetMapping("/admin/settings")
public String adminSettings(HttpServletRequest request) {
    User user = getUserFromRequest(request);
    if (user == null) return "redirect:/login";
    if (!"ADMIN".equalsIgnoreCase(user.getRole()))
        return "redirect:/?error=access_denied";
    return "admin/settings";
}
@GetMapping("/admin/visitors")
public String adminVisitors(HttpServletRequest request) {
    User user = getUserFromRequest(request);
    if (user == null) return "redirect:/login";
    if (!"ADMIN".equalsIgnoreCase(user.getRole()))
        return "redirect:/?error=access_denied";
    return "admin/visitors";
}
}