package com.example.archassistant.model.generation

data class GenerationAttemptResult(
    val rawCode: String,
    val generationTimeMs: Long
)