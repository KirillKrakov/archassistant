package com.example.archassistant.dto

import com.example.archassistant.model.ComplianceScore
import com.example.archassistant.model.StrategyType

/**
 * Ответ на запрос генерации кода
 */
data class CodeGenerationResponse(
    val success: Boolean,
    val data: GenerationData? = null,
    val error: ErrorDetails? = null,
    val metadata: ResponseMetadata
)

data class GenerationData(
    val code: String,                              // Сгенерированный код
    val score: ComplianceScore?,                   // Оценка соответствия (если была валидация)
    val strategy: StrategyType,                    // Использованная стратегия
    val iterations: Int,                           // Количество попыток генерации
    val warnings: List<String> = emptyList(),      // Предупреждения (не блокирующие)
    val suggestions: List<String> = emptyList()    // Рекомендации по улучшению
)

data class ErrorDetails(
    val code: String,          // Код ошибки: COMPILATION, VALIDATION, LLM_ERROR, etc.
    val message: String,       // Человеко-читаемое сообщение
    val details: Map<String, Any>? = null  // Дополнительные детали для отладки
)

data class ResponseMetadata(
    val generationTimeMs: Long,
    val validationTimeMs: Long = 0,
    val totalTimeMs: Long,
    val timestamp: String = java.time.LocalDateTime.now().toString(),
    val model: String? = null  // Использованная LLM модель
) {
    companion object {
        fun fromTimes(generationMs: Long, validationMs: Long = 0, model: String? = null): ResponseMetadata {
            return ResponseMetadata(
                generationTimeMs = generationMs,
                validationTimeMs = validationMs,
                totalTimeMs = generationMs + validationMs,
                model = model
            )
        }
    }
}

/**
 * Фабрика для удобного создания ответов
 */
object GenerationResponseFactory {

    fun success(
        code: String,
        score: ComplianceScore?,
        strategy: StrategyType,
        iterations: Int,
        generationTimeMs: Long,
        validationTimeMs: Long = 0,
        model: String? = null,
        warnings: List<String> = emptyList(),
        suggestions: List<String> = emptyList()
    ): CodeGenerationResponse {
        return CodeGenerationResponse(
            success = true,
            data = GenerationData(
                code = code,
                score = score,
                strategy = strategy,
                iterations = iterations,
                warnings = warnings,
                suggestions = suggestions
            ),
            metadata = ResponseMetadata.fromTimes(
                generationMs = generationTimeMs,
                validationMs = validationTimeMs,
                model = model
            )
        )
    }

    fun error(
        errorCode: String,
        message: String,
        details: Map<String, Any>? = null,
        totalTimeMs: Long
    ): CodeGenerationResponse {
        return CodeGenerationResponse(
            success = false,
            error = ErrorDetails(code = errorCode, message = message, details = details),
            metadata = ResponseMetadata(
                generationTimeMs = 0,
                totalTimeMs = totalTimeMs
            )
        )
    }
}