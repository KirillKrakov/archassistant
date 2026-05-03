package com.example.archassistant.util

import com.example.archassistant.model.ArchitecturalRule

object PromptFormatter {

    fun formatSystemPrompt(rules: List<ArchitecturalRule>): String {
        val language = detectLanguage(rules)

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
            Ты опытный разработчик на $language.
            Твоя задача — генерировать чистый, эффективный код, соблюдая архитектурные стандарты проекта.

            $rulesSection

            ИНСТРУКЦИИ:
            1. Возвращай ТОЛЬКО чистый код, без объяснений, комментариев или markdown.
            2. Используй лучшие практики для $language проектов.
            3. Если правило противоречит запросу, приоритет у архитектурного правила.
            4. Если запрос неполный, сделай разумные предположения и сгенерируй рабочий код.
            5. Если для решения нужны несколько классов, возвращай их как отдельные полноценные source files.
            6. Каждый source file должен начинаться со своей собственной package declaration.
            7. Никогда не объединяй несколько package declaration в один файл.
            8. Если в user prompt есть контекст проекта, используй только существующие пакеты, классы и методы из него 
            Никогда не генерируй заново класс, который уже присутствует в списке existingClassSignatures.
            Если тебе нужны дополнительные методы в существующем классе – это не твоя задача, просто используй его.
            9. Не выдумывай package roots, которых нет в контексте проекта.
            10. Если класс, который тебе нужен (репозиторий, сервис, DTO, сущность),
            НЕ перечислен в контексте проекта, ты обязан создать его отдельным файлом
            в этом же ответе. Запрещено использовать класс, которого нет ни в контексте,
            ни среди сгенерированных файлов.
            11. НИКОГДА не пересоздавай классы, которые уже есть в контексте проекта.
            Любой класс из секции existingClassSignatures уже существует в проекте – повторно генерировать его запрещено.
            12. ИСПОЛЬЗУЙ ТОЛЬКО проверенные, стандартные и современные пути для аннотаций и библиотек.
            13. Каждый сгенерированный source file обязан содержать все импорты,
            необходимые для его компиляции без ошибок.
            Включай импорты для любых используемых типов из стандартной библиотеки
            (java.time.LocalDate, java.util.List, java.util.Optional и т.д.),
            а также для всех аннотаций и внешних классов.
            Не полагайся на то, что импорт "уже есть где‑то в проекте".
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
                val target = rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: "*"
                "$prefix${rule.name}: классы в `${rule.fromPackage}` не должны зависеть от `$target`"
            }

            com.example.archassistant.model.RuleType.NAMING_CONVENTION -> {
                "$prefix${rule.name}: классы в `${rule.fromPackage}` должны ${
                    if (rule.constraint == com.example.archassistant.model.ConstraintType.NAMING_SUFFIX) {
                        "заканчиваться на"
                    } else {
                        "начинаться с"
                    }
                } `${rule.pattern}`"
            }

            com.example.archassistant.model.RuleType.ANNOTATION_CHECK -> {
                "$prefix${rule.name}: классы в `${rule.fromPackage}` должны ${
                    if (rule.constraint == com.example.archassistant.model.ConstraintType.HAS_ANNOTATION) {
                        "иметь"
                    } else {
                        "не иметь"
                    }
                } аннотацию `${rule.annotation}`"
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

    fun formatUserPrompt(
        originalRequest: String,
        previousErrors: List<String> = emptyList(),
        projectContext: String? = null,
        codeContext: String? = null
    ): String {
        val projectSection = projectContext?.takeIf { it.isNotBlank() }?.let {
            """
            |КОНТЕКСТ ПРОЕКТА:
            |```text
            |$it
            |```
            """.trimMargin()
        } ?: ""

        val codeSection = codeContext?.takeIf { it.isNotBlank() }?.let {
            """
            |РЕФЕРЕНСНЫЙ КОД ИЗ ПРОЕКТА:
            |```text
            |$it
            |```
            """.trimMargin()
        } ?: ""

        val errorSection = if (previousErrors.isNotEmpty()) {
            """
            |⚠️ ПРЕДЫДУЩАЯ ВЕРСИЯ НАРУШАЛА СЛЕДУЮЩИЕ ПРАВИЛА:
            |${previousErrors.joinToString("\n") { "- $it" }}
            |
            |Исправь эти ошибки в новой версии.
            """.trimMargin()
        } else {
            ""
        }

        return """
            $originalRequest

            $projectSection
            $codeSection
            $errorSection

            Сгенерируй код сейчас.
            Если нужно несколько классов, выведи их как отдельные полноценные файлы,
            каждый со своей package declaration, без смешивания нескольких package в одном файле.
            Используй только существующие пакеты и сигнатуры из контекста проекта.
            Каждый используемый класс (репозиторий, сервис, DTO, сущность)
            должен либо присутствовать в секции `existingClassSignatures` переданного контекста,
            либо быть сгенерирован отдельным файлом в этом же ответе.
            Никогда не ссылайся на класс, которого нет ни там, ни там.
            При генерации недостающего класса полностью реализуй все необходимые методы с корректными сигнатурами.
        """.trimIndent()
    }
}