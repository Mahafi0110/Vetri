package com.example.demo.repository;

import com.example.demo.model.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginHistoryRepository
        extends JpaRepository<LoginHistory, Long> {

    // Total logins today
    long countByLoggedInAtBetween(
        LocalDateTime start, LocalDateTime end
    );

    // Unique users today
    @Query("SELECT COUNT(DISTINCT l.userId) " +
           "FROM LoginHistory l " +
           "WHERE l.loggedInAt BETWEEN " +
           ":start AND :end")
    long countDistinctUserIdByLoggedInAtBetween(
        @Param("start") LocalDateTime start,
        @Param("end")   LocalDateTime end
    );

    // Recent logins
    List<LoginHistory> findTop10ByOrderByLoggedInAtDesc();

    // Recent logins today
    List<LoginHistory> findTop10ByLoggedInAtBetweenOrderByLoggedInAtDesc(
        LocalDateTime start, LocalDateTime end
    );

    // Logins by country
    @Query("SELECT l.country, COUNT(l) as cnt " +
           "FROM LoginHistory l " +
           "GROUP BY l.country " +
           "ORDER BY cnt DESC")
    List<Object[]> findLoginsByCountry();


// Get all login logs, most recent first
List<LoginHistory> findTop100ByOrderByLoggedInAtDesc();

@Query("SELECT COUNT(DISTINCT l.userEmail) " +
       "FROM LoginHistory l " +
       "WHERE l.loggedInAt BETWEEN :start AND :end")
long countDistinctEmailsBetween(
    @Param("start") LocalDateTime start,
    @Param("end")   LocalDateTime end
);
}