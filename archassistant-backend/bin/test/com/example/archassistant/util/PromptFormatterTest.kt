package com.example.archassistant.util

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PromptFormatterTest {

    @Test
    fun `formatSystemPrompt includes rules with severity prefixes`() {
        val rules = listOf(
            ArchitecturalRule(
                id = "critical",
                name = "Critical rule",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY,
                severity = Severity.CRITICAL
            ),
            ArchitecturalRule(
                id = "warning",
                name = "Warning rule",
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..",
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service",
                severity = Severity.WARNING
            )
        )

        val prompt = PromptFormatter.formatSystemPrompt(rules)

        assertTrue(prompt.contains("[ОБЯЗАТЕЛЬНО] Critical rule"))
        assertTrue(prompt.contains("[РЕКОМЕНДУЕТСЯ] Warning rule"))
        assertTrue(prompt.contains("не должны зависеть от"))
        assertTrue(prompt.contains("заканчиваться на `Service`"))
    }

    @Test
    fun `formatSystemPrompt handles empty rules`() {
        val prompt = PromptFormatter.formatSystemPrompt(emptyList())

        // Должен содержать базовые инструкции, но не секцию правил
        assertTrue(prompt.contains("Ты опытный разработчик"))
        assertFalse(prompt.contains("АРХИТЕКТУРНЫЕ ПРАВИЛА ПРОЕКТА"))
    }

    @Test
    fun `formatUserPrompt includes previous errors for regeneration`() {
        val errors = listOf(
            "UserService depends on UserController",
            "Missing @Service annotation"
        )

        val prompt = PromptFormatter.formatUserPrompt(
            originalRequest = "Create UserService",
            previousErrors = errors
        )

        assertTrue(prompt.contains("⚠️ ПРЕДУПРЕЖДЕНИЕ"))
        assertTrue(prompt.contains("UserService depends on UserController"))
        assertTrue(prompt.contains("Исправь эти ошибки"))
    }

    @Test
    fun `formatUserPrompt includes code context`() {
        val context = "public class ExampleService { ... }"

        val prompt = PromptFormatter.formatUserPrompt(
            originalRequest = "Create similar service",
            codeContext = context
        )

        assertTrue(prompt.contains("ПРИМЕРЫ ИЗ ПРОЕКТА"))
        assertTrue(prompt.contains(context))
    }

    @Test
    fun `formatSystemPrompt correctly formats rules of different types`() {
        val rules = listOf(
            ArchitecturalRule(
                id = "dep",
                name = "No controller dependency",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY
            ),
            ArchitecturalRule(
                id = "name",
                name = "Service suffix",
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..",
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service"
            ),
            ArchitecturalRule(
                id = "ann",
                name = "Must have @Service",
                type = RuleType.ANNOTATION_CHECK,
                fromPackage = "..service..",
                constraint = ConstraintType.HAS_ANNOTATION,
                annotation = "org.springframework.stereotype.Service"
            )
        )

        val prompt = PromptFormatter.formatSystemPrompt(rules)

        // Проверяем, что все три правила попали в итоговый промпт
        assertTrue(prompt.contains("не должны зависеть от"))
        assertTrue(prompt.contains("заканчиваться на `Service`"))
        assertTrue(prompt.contains("иметь аннотацию"))
    }
}