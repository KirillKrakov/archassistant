package com.example.archassistant.dto.generation.response

data class ErrorDetails(
    val code: String,          // Код ошибки: COMPILATION, VALIDATION, LLM_ERROR, etc.
    val message: String,       // Человеко-читаемое сообщение
    val details: Map<String, Any>? = null  // Дополнительные детали для отладки
)