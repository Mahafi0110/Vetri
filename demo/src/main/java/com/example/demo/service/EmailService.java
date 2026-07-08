package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

   // ✅ Fix — never crash app because of email
public void sendEmail(String to, String subject, String body) {
    try {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        System.out.println("Email sent to: " + to);
    } catch (Exception e) {
        // ✅ just log — never crash the app
        System.out.println("Email failed: " + e.getMessage());
    }
}
}
