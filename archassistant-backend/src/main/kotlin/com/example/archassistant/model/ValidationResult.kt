package com.example.archassistant.model

/**
 * Результат валидации кода через ArchUnit
 */
data class ValidationResult(
    val passed: Boolean,
    val violations: List<Violation> = emptyList(),
    val message: String? = null,
    val executionTimeMs: Long = 0
) {
    companion object {
        fun success(message: String = "Validation passed"): ValidationResult =
            ValidationResult(passed = true, message = message)

        fun failure(violations: List<Violation>, message: String? = null): ValidationResult =
             ValidationResult(
                passed = false,
                violations = violations,
                message = message ?: "Validation failed with ${violations.size} violation(s)"
            )
    }
}

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

/**
 * Уровень серьёзности нарушения
 */
enum class Severity {
    INFO,      // Информационное, не блокирует
    WARNING,   // Предупреждение, рекомендуется исправить
    ERROR,     // Ошибка, требует исправления
    CRITICAL   // Критическая ошибка, блокирует принятие кода
}