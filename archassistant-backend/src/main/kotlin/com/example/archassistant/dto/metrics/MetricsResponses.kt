package com.example.archassistant.dto.metrics

import com.example.archassistant.dto.StrategyMetrics
import java.time.LocalDateTime

data class StrategyMetricsOverviewResponse(
    val projectId: String? = null,
    val metrics: List<StrategyMetrics>
)

data class ProjectMetricsResponse(
    val projectId: String,
    val totalGenerations: Long,
    val successfulGenerations: Long,
    val failedGenerations: Long,
    val avgScore: Double?,
    val avgIterations: Double?,
    val avgGenerationTimeMs: Double?,
    val avgValidationTimeMs: Double?,
    val avgTotalTimeMs: Double?,
    val successRate: Double?,
    val successRatePercent: Double?,
    val lastGeneration: String?,
    val recentHistory: List<ProjectMetricsHistoryItem>
)

data class ProjectMetricsHistoryItem(
    val id: String,
    val strategy: String,
    val score: Double?,
    val iterations: Int,
    val success: Boolean,
    val generationTimeMs: Long,
    val validationTimeMs: Long,
    val totalTimeMs: Long,
    val violationsCount: Int,
    val timestamp: String,
    val prompt: String?,
    val generatedCode: String?
)

data class GenerationHistoryResponse(
    val projectId: String,
    val page: Int,
    val size: Int,
    val totalRecords: Long,
    val totalPages: Int,
    val records: List<GenerationHistoryRecord>
)

data class GenerationHistoryRecord(
    val id: String,
    val strategy: String,
    val success: Boolean,
    val score: Double?,
    val iterations: Int,
    val generationTimeMs: Long,
    val validationTimeMs: Long,
    val totalTimeMs: Long,
    val violationsCount: Int,
    val createdAt: LocalDateTime
)

data class ClearMetricsResponse(
    val success: Boolean,
    val projectId: String,
    val deletedCount: Int
)

data class SaveGenerationRecordResponse(
    val success: Boolean,
    val recordId: String
)