package com.example.archassistant.exception

class LlmGenerationException(
    message: String,
    val isRetryable: Boolean = true
) : RuntimeException(message)