package com.example.archassistant.service.generation

class LlmGenerationException(
    message: String,
    val isRetryable: Boolean = true
) : RuntimeException(message)