package com.example.archassistant.dto.metrics.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
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
    val prompt: String? = null,
    val generatedCode: String? = null
)