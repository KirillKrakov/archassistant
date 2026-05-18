package com.example.archassistant.dto.metrics.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GenerationHistoryItem(
    val id: String,
    val strategy: String,
    val success: Boolean,
    val score: Double?,
    val iterations: Int,
    val generationTimeMs: Long,
    val validationTimeMs: Long,
    val totalTimeMs: Long,
    val violationsCount: Int,
    val createdAt: String
)