package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "location")
    private String location;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "status")
    private String status;

    // success / failed

    @Column(name = "logged_in_at")
    private LocalDateTime loggedInAt;

    @PrePersist
    protected void onCreate() {
        loggedInAt = LocalDateTime.now();
    }
}