package com.example.archassistant.dto.generation.request

import com.example.archassistant.model.core.StrategyType


/**
 * Запрос на генерацию кода с архитектурным контролем
 */
data class CodeGenerationRequest(
    val prompt: String,                              // Запрос разработчика
    val projectId: String,                           // Идентификатор проекта
    val strategy: StrategyType = StrategyType.HYBRID,// Выбранная стратегия
    val maxIterations: Int = 3,                      // Максимум попыток перегенерации
    val context: GenerationContext? = null,          // Дополнительный контекст
    val rules: List<String>? = null,                  // Опционально: конкретные правила для этой генерации
    val collectMetrics: Boolean = false,        // Включать ли расчёт метрик
    val expectedClassName: String? = null,      // FIXED: ожидаемое имя класса для валидации
    val classpath: String? = null               // Classpath для компиляции при валидации
)