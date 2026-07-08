package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        try {
            AuthResponse authResponse = authService.register(request);
            setJwtCookie(response, authResponse.getToken(),
                    7 * 24 * 60 * 60, isHttps(httpRequest));
            return ResponseEntity.ok(
                    ApiResponse.success("Registration successful!", authResponse));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        try {
            AuthResponse authResponse = authService.login(request);
            int maxAge = request.isRememberMe() ? 30 * 24 * 60 * 60 : -1;
            setJwtCookie(response, authResponse.getToken(),
                    maxAge, isHttps(httpRequest));
            return ResponseEntity.ok(
                    ApiResponse.success("Login successful!", authResponse));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        Cookie cookie = new Cookie("vetri_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(isHttps(httpRequest));
        response.addCookie(cookie);
        return ResponseEntity.ok(
                ApiResponse.success("Logged out successfully", null));
    }

    // ── Detects if request came over HTTPS ─────────────
    private boolean isHttps(HttpServletRequest request) {
        // Check X-Forwarded-Proto header (set by Render/proxies)
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        // Fallback to request scheme
        return "https".equalsIgnoreCase(request.getScheme());
    }

    private void setJwtCookie(HttpServletResponse response,
            String token, int maxAge, boolean secure) {
        Cookie cookie = new Cookie("vetri_token", token);
        cookie.setHttpOnly(true); // JS cannot read it
        cookie.setPath("/"); // sent on every request
        cookie.setMaxAge(maxAge);
        cookie.setSecure(secure); // true on HTTPS (Render), false on localhost
        response.addCookie(cookie);
    }

   @PostMapping("/forgot-password")
public ResponseEntity<?> forgotPassword(@RequestParam String email) {
    authService.forgotPassword(email);
    return ResponseEntity.ok("If the email exists, a reset link has been sent");
}


    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
            @RequestParam String newPassword) {
        authService.resetPassword(token, newPassword);
        return "Password updated successfully";
    }
}
