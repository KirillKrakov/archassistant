package com.example.archassistant.util.archunit

object ArchUnitValidationUtils {

    /**
     * ArchUnit часто пишет:
     * "failed to check any classes..."
     * Это означает, что ни один generated class не попал под that()-selector правила.
     * Для нашей валидации это не нарушение, а "rule not applicable".
     */
    fun isNoApplicableClassesFailure(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val normalized = message.lowercase()
        return normalized.contains("failed to check any classes") ||
                normalized.contains("no classes have been passed to the rule") ||
                normalized.contains("no classes passed to the rule matched the `that()` clause")
    }
}