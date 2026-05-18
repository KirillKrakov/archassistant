package com.example.archassistant.dto.metrics.response

import com.example.archassistant.model.core.StrategyType
import java.util.Locale

data class StrategyMetrics(
    val strategy: StrategyType,
    val avgScore: Double?,
    val avgIterations: Double?,
    val successRate: Double?,
    val avgGenerationTimeMs: Double?,
    val avgValidationTimeMs: Double?,
    val avgTotalTimeMs: Double?
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "avgScore" to avgScore?.round2(),
        "avgIterations" to avgIterations?.round2(),
        "successRate" to successRate?.round2(),
        "successRatePercent" to successRate?.percent2(),
        "avgGenerationTimeMs" to avgGenerationTimeMs?.round2(),
        "avgValidationTimeMs" to avgValidationTimeMs?.round2(),
        "avgTotalTimeMs" to avgTotalTimeMs?.round2()
    )

    private fun Double.round2(): Double = String.format(Locale.US, "%.2f", this).toDouble()
    private fun Double.percent2(): Double = String.format(Locale.US, "%.2f", this * 100.0).toDouble()
}