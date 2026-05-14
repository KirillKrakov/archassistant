package com.example.archassistant.dto.generation.response

import com.example.archassistant.model.StrategyType
import com.example.archassistant.model.core.ComplianceScore

data class GenerationData(
    val code: String,                              // Сгенерированный код
    val score: ComplianceScore?,                   // Оценка соответствия (если была валидация)
    val strategy: StrategyType,                    // Использованная стратегия
    val iterations: Int,                           // Количество попыток генерации
    val warnings: List<String> = emptyList(),      // Предупреждения (не блокирующие)
    val suggestions: List<String> = emptyList()    // Рекомендации по улучшению
)