package com.example.demo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    

    // Existing
    public String generateToken(String email) {
        return generateToken(email, jwtExpiration);
    }

    // Generate token from email
    // public String generateToken(String email) {
    // Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    // return Jwts.builder()
    // .setSubject(email)
    // .setIssuedAt(new Date())
    // .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
    // .signWith(key, SignatureAlgorithm.HS256)
    // .compact();
    // }
   
       // Token with custom expiration (for Remember Me)
    public String generateToken(String email, long expirationMs) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Get email from token
    public String getEmailFromToken(String token) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
    

    // Validate token
    public boolean validateToken(String token) {
        try {
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
