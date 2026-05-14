package com.example.archassistant.model.core

/**
 * Описание нарушения архитектурного правила
 */
data class Violation(
    val ruleId: String,
    val description: String,
    val className: String,
    val lineNumber: Int? = null,
    val severity: Severity = Severity.WARNING
)