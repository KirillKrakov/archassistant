package com.example.archassistant.util

import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.ConstraintType
import com.example.archassistant.model.RuleType
import com.example.archassistant.model.Severity

object PromptFormatter {

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
            Ты опытный разработчик на Java/Kotlin.
            Твоя задача — генерировать чистый, эффективный код, соблюдая архитектурные стандарты проекта.

            $rulesSection

            ИНСТРУКЦИИ:
            1. Возвращай ТОЛЬКО чистый код, без объяснений, комментариев или markdown.
            2. Используй лучшие практики для Java/Kotlin проектов.
            3. Если правило противоречит запросу, приоритет у архитектурного правила.
            4. Если запрос неполный, делай разумные предположения только внутри существующего контекста проекта.
            5. Если для решения нужны несколько классов, возвращай их как отдельные полноценные source files.
            6. Каждый source file должен начинаться со своей собственной package declaration.
            7. Никогда не объединяй несколько package declaration в один файл.
            8. Если в user prompt есть контекст проекта, используй только существующие пакеты, классы и методы из него.
            9. Не выдумывай package roots, которых нет в контексте проекта.
            10. Если ты реализуешь существующий интерфейс или repository contract, включи все abstract methods с точными сигнатурами из контекста.
            11. Не генерируй частичную реализацию интерфейса: если интерфейс выбран, он должен компилироваться без пропусков abstract methods.
            12. Не меняй тип артефакта без явной необходимости: сохраняй исходный intent запроса и применяй правила к нему.
        """.trimIndent()
    }

    private fun formatRule(rule: ArchitecturalRule): String {
        val prefix = when (rule.severity) {
            Severity.CRITICAL -> "[ОБЯЗАТЕЛЬНО] "
            Severity.ERROR -> "[ТРЕБУЕТСЯ] "
            Severity.WARNING -> "[РЕКОМЕНДУЕТСЯ] "
            else -> ""
        }

        return when (rule.type) {
            RuleType.DEPENDENCY, RuleType.LAYER_ISOLATION -> {
                val target = rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: "*"
                "$prefix${rule.name}: классы в `${rule.fromPackage}` не должны зависеть от `$target`"
            }

            RuleType.NAMING_CONVENTION -> {
                val action = if (rule.constraint == ConstraintType.NAMING_SUFFIX) {
                    "заканчиваться на"
                } else {
                    "начинаться с"
                }
                "$prefix${rule.name}: классы в `${rule.fromPackage}` должны $action `${rule.pattern}`"
            }

            RuleType.ANNOTATION_CHECK -> {
                val action = if (rule.constraint == ConstraintType.HAS_ANNOTATION) {
                    "иметь"
                } else {
                    "не иметь"
                }
                "$prefix${rule.name}: классы в `${rule.fromPackage}` должны $action аннотацию `${rule.annotation}`"
            }

            RuleType.CYCLE_CHECK -> {
                val slice = rule.slicePattern ?: rule.fromPackage
                "$prefix${rule.name}: package slices `$slice` должны быть без циклических зависимостей"
            }

            RuleType.INHERITANCE_CHECK -> {
                val target = rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: rule.toClassType?.name ?: "*"
                val action = if (rule.constraint == ConstraintType.SHOULD_NOT_EXTEND) {
                    "не должны наследоваться от"
                } else {
                    "должны наследоваться от"
                }
                "$prefix${rule.name}: классы в `${rule.fromPackage}` $action `$target`"
            }

            RuleType.INTERFACE_CHECK -> {
                val target = rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: rule.toClassType?.name ?: "*"
                val action = if (rule.constraint == ConstraintType.SHOULD_NOT_IMPLEMENT) {
                    "не должны реализовывать"
                } else {
                    "должны реализовывать"
                }
                "$prefix${rule.name}: классы в `${rule.fromPackage}` $action `$target`"
            }

            RuleType.MODIFIER_CHECK -> {
                val modifier = when (rule.constraint) {
                    ConstraintType.SHOULD_BE_PUBLIC, ConstraintType.SHOULD_NOT_BE_PUBLIC -> "public"
                    ConstraintType.SHOULD_BE_FINAL, ConstraintType.SHOULD_NOT_BE_FINAL -> "final"
                    ConstraintType.SHOULD_BE_ABSTRACT, ConstraintType.SHOULD_NOT_BE_ABSTRACT -> "abstract"
                    else -> rule.constraint.name.lowercase()
                }
                val action = when (rule.constraint) {
                    ConstraintType.SHOULD_NOT_BE_PUBLIC,
                    ConstraintType.SHOULD_NOT_BE_FINAL,
                    ConstraintType.SHOULD_NOT_BE_ABSTRACT -> "не должны быть"

                    else -> "должны быть"
                }
                "$prefix${rule.name}: классы в `${rule.fromPackage}` $action `$modifier`"
            }

            RuleType.METHOD_SIGNATURE_CHECK -> {
                val parts = mutableListOf<String>()
                rule.fromMethodNamePattern?.takeIf { it.isNotBlank() }?.let { parts += "имя совпадает с шаблоном `$it`" }
                rule.fromReturnType?.takeIf { it.isNotBlank() }?.let { parts += "возвращают `$it`" }
                rule.fromParameterTypes?.takeIf { it.isNotEmpty() }?.let { parts += "принимают параметры `${it.joinToString(", ")}`" }
                rule.fromModifiers?.takeIf { it.isNotEmpty() }?.let { parts += "имеют модификаторы `${it.joinToString(", ")}`" }

                val details = if (parts.isNotEmpty()) parts.joinToString("; ") else "имеют требуемую сигнатуру"
                "$prefix${rule.name}: методы классов в `${rule.fromPackage}` должны соответствовать `${rule.constraint}`; $details"
            }

            RuleType.FIELD_CHECK -> {
                val parts = mutableListOf<String>()
                rule.fromFieldNamePattern?.takeIf { it.isNotBlank() }?.let { parts += "имя совпадает с шаблоном `$it`" }
                rule.fromFieldType?.takeIf { it.isNotBlank() }?.let { parts += "тип `$it`" }
                rule.annotation?.takeIf { it.isNotBlank() }?.let { parts += "аннотация `$it`" }
                rule.fromModifiers?.takeIf { it.isNotEmpty() }?.let { parts += "модификаторы `${it.joinToString(", ")}`" }

                val details = if (parts.isNotEmpty()) parts.joinToString("; ") else "соответствуют требуемому свойству"
                "$prefix${rule.name}: поля классов в `${rule.fromPackage}` должны соответствовать `${rule.constraint}`; $details"
            }

            RuleType.EXCEPTION_CHECK -> {
                val target = rule.fromThrowsTypes?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: rule.toThrowsTypes?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: "*"

                val action = when (rule.constraint) {
                    ConstraintType.SHOULD_ONLY_THROW -> "могут выбрасывать только"
                    ConstraintType.SHOULD_NOT_THROW -> "не должны выбрасывать"
                    else -> "должны соблюдать ограничение на исключения"
                }

                "$prefix${rule.name}: методы классов в `${rule.fromPackage}` $action `$target`"
            }

            RuleType.CUSTOM -> "$prefix${rule.name}: ${rule.description ?: rule.id}"
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
            Не добавляй импорты из пакетов, которых нет в knownPackages.
            Сохраняй исходный intent запроса и не меняй тип артефакта без явной необходимости.
        """.trimIndent()
    }
}