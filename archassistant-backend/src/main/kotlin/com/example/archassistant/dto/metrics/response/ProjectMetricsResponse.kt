package com.example.archassistant.dto.metrics.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProjectMetricsResponse(
    val projectId: String,
    val totalGenerations: Long,
    val successfulGenerations: Long,
    val failedGenerations: Long,
    val avgScore: Double? = null,
    val avgIterations: Double? = null,
    val avgGenerationTimeMs: Double? = null,
    val avgValidationTimeMs: Double? = null,
    val avgTotalTimeMs: Double? = null,
    val successRate: Double? = null,
    val successRatePercent: Double? = null,
    val lastGeneration: String? = null,
    val recentHistory: List<ProjectMetricsHistoryItem> = emptyList()
)