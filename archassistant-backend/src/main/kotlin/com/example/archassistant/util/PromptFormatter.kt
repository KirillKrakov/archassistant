package com.example.archassistant.util

import com.example.archassistant.model.ArchitecturalRule

object PromptFormatter {

    /**
     * Формирование system prompt с архитектурными правилами
     * FIXED: больше не принимает projectContext — контекст в userPrompt
     */
    fun formatSystemPrompt(rules: List<ArchitecturalRule>): String {
        val rulesSection = if (rules.isNotEmpty()) {
            """
            |АРХИТЕКТУРНЫЕ ПРАВИЛА ПРОЕКТА (обязательны к соблюдению):
            |${rules.joinToString("\n") { formatRule(it) }}
            |
            |Если сгенерированный код нарушает эти правила, он будет отклонён.
            """.trimMargin()
        } else {
            ""
        }

        return """
            Ты опытный разработчик на ${detectLanguage(rules)}. 
            Твоя задача — генерировать чистый, эффективный код, соблюдая архитектурные стандарты проекта.
            
            $rulesSection
            
            ИНСТРУКЦИИ:
            1. Возвращай ТОЛЬКО код, без объяснений, комментариев или маркдауна.
            2. Используй лучшие практики для ${detectLanguage(rules)} проектов.
            3. Если правило противоречит запросу, приоритет у архитектурного правила.
            4. Если запрос неполный, сделай разумные предположения и сгенерируй рабочий код.
        """.trimIndent()
    }

    private fun formatRule(rule: ArchitecturalRule): String {
        val prefix = when (rule.severity) {
            com.example.archassistant.model.Severity.CRITICAL -> "[ОБЯЗАТЕЛЬНО] "
            com.example.archassistant.model.Severity.ERROR -> "[ТРЕБУЕТСЯ] "
            com.example.archassistant.model.Severity.WARNING -> "[РЕКОМЕНДУЕТСЯ] "
            else -> ""
        }

        return when (rule.type) {
            com.example.archassistant.model.RuleType.DEPENDENCY -> {
                "$prefix${rule.name}: классы в `${rule.fromPackage}` не должны зависеть от `${rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: "*"}"
            }
            com.example.archassistant.model.RuleType.NAMING_CONVENTION -> {
                "$prefix${rule.name}: классы в `${rule.fromPackage}` должны ${if (rule.constraint == com.example.archassistant.model.ConstraintType.NAMING_SUFFIX) "заканчиваться на" else "начинаться с"} `${rule.pattern}`"
            }
            com.example.archassistant.model.RuleType.ANNOTATION_CHECK -> {
                "$prefix${rule.name}: классы в `${rule.fromPackage}` должны ${if (rule.constraint == com.example.archassistant.model.ConstraintType.HAS_ANNOTATION) "иметь" else "не иметь"} аннотацию `${rule.annotation}`"
            }
            else -> "$prefix${rule.name}: ${rule.description ?: rule.id}"
        }
    }

    private fun detectLanguage(rules: List<ArchitecturalRule>): String {
        return if (rules.any { it.annotation?.contains("org.springframework") == true }) {
            "Java/Kotlin (Spring Boot)"
        } else {
            "Java/Kotlin"
        }
    }

    /**
     * Формирование user prompt с учётом контекста и истории ошибок
     * FIXED: codeContext теперь добавляется сюда, а не в system prompt
     */
    fun formatUserPrompt(
        originalRequest: String,
        previousErrors: List<String> = emptyList(),
        codeContext: String? = null
    ): String {
        val errorSection = if (previousErrors.isNotEmpty()) {
            """
            |⚠️ ПРЕДУПРЕЖДЕНИЕ: Предыдущая версия кода нарушала следующие правила:
            |${previousErrors.joinToString("\n") { "- $it" }}
            |
            |Исправь эти ошибки в новой версии.
            """.trimMargin()
        } else {
            ""
        }

        // FIXED: контекст добавляется в user prompt, не в system prompt
        val contextSection = codeContext?.let {
            """
            |ПРИМЕРЫ ИЗ ПРОЕКТА (используй как референс для стиля):
            |```
            |$it
            |```
            """.trimMargin()
        } ?: ""

        return """
            $originalRequest
            
            $contextSection
            $errorSection
            
            Сгенерируй код сейчас.
        """.trimIndent()
    }
}