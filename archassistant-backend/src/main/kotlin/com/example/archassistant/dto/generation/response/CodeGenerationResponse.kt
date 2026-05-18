package com.example.archassistant.dto.generation.response

/**
 * Ответ на запрос генерации кода
 */
data class CodeGenerationResponse(
    val success: Boolean,
    val data: GenerationData? = null,
    val error: ErrorDetails? = null,
    val metadata: ResponseMetadata
)