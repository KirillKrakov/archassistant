package com.example.archassistant.model.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Градация качества архитектурного соответствия
 * Используется для быстрой визуальной оценки результата
 */
enum class ScoreGrade(
    val minScore: Double,
    val maxScore: Double,
    val description: String,
    val color: String  // Для UI: например, цвет прогресс-бара
) {
    EXCELLENT(90.0, 100.0, "Отличное соответствие архитектурным стандартам", "#22c55e"),   // green
    GOOD(80.0, 89.99, "Хорошее соответствие, мелкие замечания", "#84cc16"),              // lime
    ACCEPTABLE(70.0, 79.99, "Приемлемое соответствие, требуются улучшения", "#f59e0b"),  // amber
    NEEDS_IMPROVEMENT(50.0, 69.99, "Требует значительных улучшений", "#ef4444"),          // red
    FAIL(0.0, 49.99, "Критические нарушения архитектуры", "#dc2626");                     // red-600

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromScore(score: Double): ScoreGrade {
            return entries.find { grade ->
                score in grade.minScore..grade.maxScore
            } ?: FAIL
        }

        @JvmStatic
        fun fromValue(value: String): ScoreGrade {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: FAIL
        }
    }

    @JsonValue
    fun toValue(): String = name

    /**
     * Проверка, проходит ли код порог качества
     */
    fun isPassing(threshold: Double = 70.0): Boolean {
        return minScore >= threshold
    }
}