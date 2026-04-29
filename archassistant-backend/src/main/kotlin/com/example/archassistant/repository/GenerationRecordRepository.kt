package com.example.archassistant.repository

import com.example.archassistant.dto.StrategyMetrics
import com.example.archassistant.model.GenerationRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

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
}