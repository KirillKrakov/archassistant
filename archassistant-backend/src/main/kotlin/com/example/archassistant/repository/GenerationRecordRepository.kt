package com.example.archassistant.repository

import com.example.archassistant.dto.StrategyMetrics
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface GenerationRecordRepository : JpaRepository<GenerationRecord, String> {

    @Query("""
        SELECT new com.example.archassistant.dto.StrategyMetrics(
            g.strategy,
            AVG(g.scoreTotal),
            AVG(g.iterations),
            CAST(SUM(CASE WHEN g.success = true THEN 1 ELSE 0 END) AS double) / COUNT(*),
            AVG(g.generationTimeMs + g.validationTimeMs)
        )
        FROM GenerationRecord g
        WHERE g.projectId = :projectId
        GROUP BY g.strategy
    """)
    fun getMetricsByProject(@Param("projectId") projectId: String): List<StrategyMetrics>

    @Query("""
        SELECT new com.example.archassistant.dto.StrategyMetrics(
            g.strategy,
            AVG(g.scoreTotal),
            AVG(g.iterations),
            CAST(SUM(CASE WHEN g.success = true THEN 1 ELSE 0 END) AS double) / COUNT(*),
            AVG(g.generationTimeMs + g.validationTimeMs)
        )
        FROM GenerationRecord g
        GROUP BY g.strategy
    """)
    fun getAllMetrics(): List<StrategyMetrics>

    fun findByProjectIdOrderByCreatedAtDesc(projectId: String): List<GenerationRecord>

    fun countByProjectId(projectId: String): Long

    @Query("""
    SELECT r FROM GenerationRecord r 
    WHERE r.projectId = :projectId 
    AND r.strategy = :strategy 
    AND r.createdAt BETWEEN :fromDate AND :toDate
    ORDER BY r.createdAt DESC
""")
    fun findByProjectIdAndStrategyAndCreatedAtBetween(
        @Param("projectId") projectId: String,
        @Param("strategy") strategy: StrategyType,
        @Param("fromDate") fromDate: LocalDateTime,
        @Param("toDate") toDate: LocalDateTime
    ): List<GenerationRecord>

    @Query("""
    SELECT r FROM GenerationRecord r 
    WHERE r.projectId = :projectId 
    AND r.strategy = :strategy
    ORDER BY r.createdAt DESC
""")
    fun findByProjectIdAndStrategy(
        @Param("projectId") projectId: String,
        @Param("strategy") strategy: StrategyType
    ): List<GenerationRecord>

    @Query("""
    SELECT r FROM GenerationRecord r 
    WHERE r.projectId = :projectId 
    AND r.createdAt BETWEEN :fromDate AND :toDate
    ORDER BY r.createdAt DESC
""")
    fun findByProjectIdAndCreatedAtBetween(
        @Param("projectId") projectId: String,
        @Param("fromDate") fromDate: LocalDateTime,
        @Param("toDate") toDate: LocalDateTime
    ): List<GenerationRecord>

    @Query("""
    SELECT r FROM GenerationRecord r 
    WHERE r.createdAt BETWEEN :fromDate AND :toDate
    ORDER BY r.createdAt DESC
""")
    fun findByCreatedAtBetween(
        @Param("fromDate") fromDate: LocalDateTime,
        @Param("toDate") toDate: LocalDateTime
    ): List<GenerationRecord>
}