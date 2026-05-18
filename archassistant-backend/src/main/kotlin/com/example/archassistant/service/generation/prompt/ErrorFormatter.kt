package com.example.archassistant.service.generation.prompt

import com.example.archassistant.model.core.Severity
import com.example.archassistant.model.core.Violation

/**
 * Форматирование нарушений архитектурных правил в текст для обратной связи LLM
 */
object ErrorFormatter {

    /**
     * Преобразование списка нарушений в человекочитаемый текст для промпта
     */
    fun formatErrorsForPrompt(violations: List<Violation>, maxErrors: Int = 5): String {
        if (violations.isEmpty()) return ""

        val limited = violations.take(maxErrors)
        val moreCount = violations.size - maxErrors

        return limited.joinToString("\n") { violation ->
            when (violation.severity) {
                Severity.CRITICAL -> "[КРИТИЧНО] "
                Severity.ERROR -> "[ОШИБКА] "
                Severity.WARNING -> "[ПРЕДУПРЕЖДЕНИЕ] "
                else -> ""
            } + "${violation.description}" +
                    (if (violation.className != "*") " (класс: ${violation.className})" else "")
        } + if (moreCount > 0) "\n... и ещё $moreCount нарушений" else ""
    }

    /**
     * Формирование инструкции по исправлению для LLM
     */
    fun formatFixInstruction(violations: List<Violation>, maxErrors: Int = 5): String {
        if (violations.isEmpty()) return ""

        val critical = violations.filter { it.severity == Severity.CRITICAL }
        val errors = violations.filter { it.severity == Severity.ERROR }

        return buildString {
            if (critical.isNotEmpty()) {
                appendLine("❗ КРИТИЧЕСКИЕ нарушения (обязательно исправить):")
                appendLine(formatErrorsForPrompt(critical.take(maxErrors), maxErrors))
                appendLine()
            }
            if (errors.isNotEmpty()) {
                appendLine("⚠️ Ошибки (рекомендуется исправить):")
                appendLine(formatErrorsForPrompt(errors.take(maxErrors), maxErrors))
                appendLine()
            }
            appendLine("Исправь эти нарушения в новой версии кода. Возвращай ТОЛЬКО исправленный код.")
        }
    }
}