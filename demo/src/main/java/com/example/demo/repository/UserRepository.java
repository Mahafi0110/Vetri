package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Find user by email — used in login
    Optional<User> findByEmail(String email);

    // Check if email already exists — used in register
    boolean existsByEmail(String email);

    // Add this to existing UserRepository.java
    long countByCreatedAtBetween(
            LocalDateTime start, LocalDateTime end);

    // Get all users
    List<User> findAllByOrderByCreatedAtDesc();
}
