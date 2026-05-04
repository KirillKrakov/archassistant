package com.example.archassistant.dto

import com.example.archassistant.model.StrategyType

/**
 * Результат сравнения стратегий
 */
data class ComparisonResult(
    val projectId: String? = null,  // null = сравнение по всем проектам
    val strategies: Map<StrategyType, StrategyComparison>,
    val recommendation: Recommendation? = null,
    val comparedAt: String = java.time.LocalDateTime.now().toString()
)

data class StrategyComparison(
    val strategy: StrategyType,
    val totalGenerations: Long,
    val successRate: Double,          // 0.0 - 1.0
    val avgScore: Double?,            // средняя оценка качества
    val avgIterations: Double,        // среднее число итераций
    val avgGenerationTimeMs: Double,  // среднее время генерации
    val avgValidationTimeMs: Double,  // среднее время валидации
    val avgViolations: Double         // среднее число нарушений
)

data class Recommendation(
    val bestStrategy: StrategyType,
    val reason: String,
    val confidence: Double  // 0.0 - 1.0
)