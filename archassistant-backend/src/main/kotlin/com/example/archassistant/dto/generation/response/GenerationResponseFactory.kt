package com.example.archassistant.dto.generation.response

import com.example.archassistant.model.ComplianceScore
import com.example.archassistant.model.StrategyType

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