package com.example.archassistant.dto

import com.example.archassistant.model.StrategyType

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

/**
 * Дополнительный контекст для генерации
 */
data class GenerationContext(
    val entity: String? = null,           // Сущность, для которой генерируется код
    val packageName: String? = null,      // Целевой пакет
    val classType: String? = null,        // Тип класса: Service, Repository, etc.
    val dependencies: List<String>? = null,// Ожидаемые зависимости
    val codeSnippet: String? = null       // Существующий код для контекста (RAG)
)