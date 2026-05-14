package com.example.archassistant.service.generation.pipeline

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.model.GenerationValidationResult
import com.example.archassistant.model.StrategyType
import org.springframework.stereotype.Service

@Service
class StrategyIterationPolicy(
    private val properties: ArchassistantProperties
) {

    fun shouldRetry(strategyType: StrategyType): Boolean {
        return strategyType != StrategyType.PRE
    }

    fun isSuccessful(result: GenerationValidationResult): Boolean {
        val score = result.score ?: return false
        return result.violations.isEmpty() && score.total >= properties.compliance.threshold
    }

    fun displayName(strategyType: StrategyType): String {
        return when (strategyType) {
            StrategyType.PRE -> "Pre"
            StrategyType.POST -> "Post"
            StrategyType.HYBRID -> "Hybrid"
        }
    }

    fun retryWarning(strategyType: StrategyType, iteration: Int, maxIterations: Int): String {
        return "${displayName(strategyType)}-Strategy required $iteration/$maxIterations iterations."
    }

    fun exhaustionWarning(strategyType: StrategyType, maxIterations: Int): String {
        return "${displayName(strategyType)}-Strategy exhausted $maxIterations iterations. Manual review is recommended."
    }

    fun lowScoreWarning(score: Double): String {
        return "Compliance Score ${score}% is below threshold ${properties.compliance.threshold}%."
    }
}