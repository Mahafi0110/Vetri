package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "page_visits")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

    @Column(name = "device_type")
    private String deviceType;  // Mobile / Tablet / Laptop

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "visited_at")
    private LocalDateTime visitedAt;

    @Column(name = "visitor_id")
private String visitorId;

    @PrePersist
    protected void onCreate() {
        visitedAt = LocalDateTime.now();
    }
}