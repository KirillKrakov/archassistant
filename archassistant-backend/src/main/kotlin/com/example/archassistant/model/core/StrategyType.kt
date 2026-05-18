package com.example.archassistant.model.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Стратегия архитектурного контроля при генерации кода
 */
enum class StrategyType(
    val description: String,
    val averageIterations: Int,
    val expectedQuality: String
) {
    PRE(
        description = "Правила добавляются в промпт до генерации. Быстро, но LLM может игнорировать правила.",
        averageIterations = 1,
        expectedQuality = "Средняя"
    ),
    POST(
        description = "Валидация после генерации + перегенерация при провале. Точно, но может потребовать несколько итераций.",
        averageIterations = 2,
        expectedQuality = "Высокая"
    ),
    HYBRID(
        description = "Правила в промпте + валидация после + перегенерация при провале. Максимальное соответствие, но медленнее.",
        averageIterations = 2,
        expectedQuality = "Максимальная"
    );

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): StrategyType {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: PRE
        }
    }

    @JsonValue
    fun toValue(): String = name
}