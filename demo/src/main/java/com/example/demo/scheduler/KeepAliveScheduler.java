// src/main/java/com/example/demo/scheduler/KeepAliveScheduler.java
package com.example.demo.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class KeepAliveScheduler {

    @Scheduled(fixedDelay = 840000) // every 14 minutes
    public void keepAlive() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getForObject(
                "https://vetrifile-1.onrender.com/actuator/health",
                String.class);
            System.out.println("Keep alive ping ✅");
        } catch (Exception e) {
            System.out.println("Keep alive failed: " + e.getMessage());
        }
    }
}
