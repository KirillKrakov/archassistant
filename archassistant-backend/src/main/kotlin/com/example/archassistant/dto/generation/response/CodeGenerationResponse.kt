package com.example.archassistant.dto.generation.response

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