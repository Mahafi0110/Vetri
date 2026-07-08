package com.example.demo.repository;

import com.example.demo.model.FileHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileHistoryRepository
        extends JpaRepository<FileHistory, Long> {

    // Files processed today
    List<FileHistory> findByProcessedAtBetween(
        LocalDateTime start, LocalDateTime end
    );

    // Files by type today
    long countByFileTypeAndProcessedAtBetween(
        String fileType,
        LocalDateTime start,
        LocalDateTime end
    );

    // Total by type all time
    long countByFileType(String fileType);

    // Success count today
    long countByStatusAndProcessedAtBetween(
        String status,
        LocalDateTime start,
        LocalDateTime end
    );

    // Most used tools today
    @Query("SELECT f.toolUsed, COUNT(f) as cnt " +
           "FROM FileHistory f " +
           "WHERE f.processedAt BETWEEN " +
           ":start AND :end " +
           "GROUP BY f.toolUsed " +
           "ORDER BY cnt DESC")
    List<Object[]> findTopToolsToday(
        @Param("start") LocalDateTime start,
        @Param("end")   LocalDateTime end
    );

    // Recent files today
    List<FileHistory> findTop10ByProcessedAtBetweenOrderByProcessedAtDesc(
        LocalDateTime start, LocalDateTime end
    );
    // Get all file logs, most recent first
List<FileHistory> findTop100ByOrderByProcessedAtDesc();
}