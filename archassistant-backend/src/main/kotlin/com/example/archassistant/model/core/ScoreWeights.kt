package com.example.archassistant.model.core

/**
 * Веса компонентов для расчёта ComplianceScore
 */
data class ScoreWeights(
    val rulesPass: Double = 1.0,          // ArchUnit правила (наиболее объективные)
    val patternMatch: Double = 0.5,       // Соответствие паттернам (частично экспертная оценка)
    val dependencyCorrect: Double = 0.5   // Корректность зависимостей
) {
    init {
        require(rulesPass >= 0 && patternMatch >= 0 && dependencyCorrect >= 0) {
            "Weights must be non-negative"
        }
    }
}