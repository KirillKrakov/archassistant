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
        fun success(message: String = "Validation passed"): ValidationResult {
            return ValidationResult(passed = true, message = message)
        }

        fun failure(violations: List<Violation>, message: String? = null): ValidationResult {
            return ValidationResult(
                passed = false,
                violations = violations,
                message = message ?: "Validation failed with ${violations.size} violation(s)"
            )
        }
    }
}