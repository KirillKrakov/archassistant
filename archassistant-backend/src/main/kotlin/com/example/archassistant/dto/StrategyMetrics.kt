package com.example.archassistant.dto

import com.example.archassistant.model.StrategyType
import java.util.*

data class StrategyMetrics(
    val strategy: StrategyType,
    val avgScore: Double?,
    val avgIterations: Double?,
    val successRate: Double?,
    val avgTotalTimeMs: Double?
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "avgScore" to avgScore?.let { String.format(Locale.US, "%.2f", it).toDouble() },
        "avgIterations" to avgIterations?.let { String.format(Locale.US, "%.2f", it).toDouble() },
        "successRate" to successRate?.let { String.format(Locale.US, "%.2f", it).toDouble() },
        "avgGenerationTimeMs" to (avgTotalTimeMs?.toLong() ?: 0)
    )
}