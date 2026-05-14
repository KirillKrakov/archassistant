package com.example.archassistant.dto

import com.example.archassistant.model.StrategyType
import java.util.Locale

data class ComparisonResult(
    val projectId: String? = null,
    val strategies: Map<StrategyType, StrategyComparison>,
    val recommendation: Recommendation? = null,
    val comparedAt: String = java.time.LocalDateTime.now().toString()
)

data class StrategyComparison(
    val strategy: StrategyType,
    val totalGenerations: Long,
    val successRate: Double,
    val avgScore: Double?,
    val avgIterations: Double,
    val avgGenerationTimeMs: Double,
    val avgValidationTimeMs: Double,
    val avgTotalTimeMs: Double,
    val avgViolations: Double
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "strategy" to strategy.name,
        "totalGenerations" to totalGenerations,
        "successRate" to successRate.round2(),
        "successRatePercent" to successRate.percent2(),
        "avgScore" to avgScore?.round2(),
        "avgIterations" to avgIterations.round2(),
        "avgGenerationTimeMs" to avgGenerationTimeMs.round2(),
        "avgValidationTimeMs" to avgValidationTimeMs.round2(),
        "avgTotalTimeMs" to avgTotalTimeMs.round2(),
        "avgViolations" to avgViolations.round2()
    )

    private fun Double.round2(): Double = String.format(Locale.US, "%.2f", this).toDouble()

    private fun Double.percent2(): Double = String.format(Locale.US, "%.2f", this * 100.0).toDouble()
}

data class Recommendation(
    val bestStrategy: StrategyType,
    val reason: String,
    val confidence: Double
)