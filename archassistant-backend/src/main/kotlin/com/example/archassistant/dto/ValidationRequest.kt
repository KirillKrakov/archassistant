package com.example.archassistant.dto

data class ValidationRequest(
    val code: String,
    val className: String? = null,   // опционально, если не указан — попробуем извлечь
    val rules: List<RuleDefinition>? = null  // пока не реализовано, для будущего
)

data class RuleDefinition(
    val fromPackage: String,
    val toPackage: String,
    val type: String = "noDependency"
)