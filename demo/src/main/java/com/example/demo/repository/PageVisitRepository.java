package com.example.demo.repository;

import com.example.demo.model.PageVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PageVisitRepository
        extends JpaRepository<PageVisit, Long> {

    // ── UNIQUE visitors in range (not row count!) ──
    @Query("SELECT COUNT(DISTINCT p.visitorId) " +
           "FROM PageVisit p " +
           "WHERE p.visitedAt BETWEEN :start AND :end")
    long countDistinctVisitorsBetween(
        @Param("start") LocalDateTime start,
        @Param("end")   LocalDateTime end
    );

    // ── One row per unique visitor, most recent visit only ──
    // (for the table display — no duplicate people)
    @Query("SELECT p FROM PageVisit p " +
           "WHERE p.visitedAt BETWEEN :start AND :end " +
           "AND p.id IN (" +
           "  SELECT MAX(p2.id) FROM PageVisit p2 " +
           "  WHERE p2.visitedAt BETWEEN :start AND :end " +
           "  GROUP BY p2.visitorId" +
           ") ORDER BY p.visitedAt DESC")
    List<PageVisit> findDistinctVisitorsBetween(
        @Param("start") LocalDateTime start,
        @Param("end")   LocalDateTime end
    );

    // ── Country-wise UNIQUE count ──
    @Query("SELECT p.country, COUNT(DISTINCT p.visitorId) as cnt " +
           "FROM PageVisit p " +
           "WHERE p.visitedAt BETWEEN :start AND :end " +
           "GROUP BY p.country ORDER BY cnt DESC")
    List<Object[]> countDistinctByCountry(
        @Param("start") LocalDateTime start,
        @Param("end")   LocalDateTime end
    );
}