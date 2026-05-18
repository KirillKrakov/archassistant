package com.example.archassistant.dto.generation.response

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