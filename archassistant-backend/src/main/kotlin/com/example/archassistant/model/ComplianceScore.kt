package com.example.archassistant.model

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Результат расчёта метрики архитектурного соответствия
 *
 * Формула:
 * Score = (W₁ × RulesPass + W₂ × PatternMatch + W₃ × DependencyCorrect) / (W₁ + W₂ + W₃)
 *
 * Где веса по умолчанию: W₁=1.0, W₂=0.5, W₃=0.5
 */
data class ComplianceScore(
    val total: Double,                    // Итоговый скор (0-100%)
    val rulesPass: Double,                // % пройденных ArchUnit правил
    val patternMatch: Double,             // % соответствия паттернам
    val dependencyCorrect: Double,        // % корректных зависимостей
    val weights: ScoreWeights = ScoreWeights(),  // Веса компонентов
    val violations: List<Violation> = emptyList(), // Детализация нарушений
    val calculatedAt: String = java.time.LocalDateTime.now().toString()
) {
    @JsonIgnore
    fun isPassing(threshold: Double = 70.0): Boolean = total >= threshold

    @JsonIgnore
    fun getGrade(): ScoreGrade {
        return when {
            total >= 90 -> ScoreGrade.EXCELLENT
            total >= 80 -> ScoreGrade.GOOD
            total >= 70 -> ScoreGrade.ACCEPTABLE
            total >= 50 -> ScoreGrade.NEEDS_IMPROVEMENT
            else -> ScoreGrade.FAIL
        }
    }

    companion object {
        /**
         * Расчёт скор по формуле с весами
         */
        fun calculate(
            rulesPass: Double,
            patternMatch: Double,
            dependencyCorrect: Double,
            weights: ScoreWeights = ScoreWeights(),
            violations: List<Violation> = emptyList()
        ): ComplianceScore {
            val total = (
                    weights.rulesPass * rulesPass +
                            weights.patternMatch * patternMatch +
                            weights.dependencyCorrect * dependencyCorrect
                    ) / (weights.rulesPass + weights.patternMatch + weights.dependencyCorrect)

            return ComplianceScore(
                total = total.coerceIn(0.0, 100.0),
                rulesPass = rulesPass.coerceIn(0.0, 100.0),
                patternMatch = patternMatch.coerceIn(0.0, 100.0),
                dependencyCorrect = dependencyCorrect.coerceIn(0.0, 100.0),
                weights = weights,
                violations = violations
            )
        }
    }
}

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