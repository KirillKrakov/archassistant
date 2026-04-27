package com.example.archassistant.util

import com.example.archassistant.model.*
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import java.nio.file.Path

object PatternMatcher {

    private val logger = LoggerFactory.getLogger(PatternMatcher::class.java)

    // ========== ОСНОВНЫЕ МЕТОДЫ ==========

    fun calculatePatternMatch(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): Double {
        val relevant = rules.filter { it.enabled && it.type in listOf(RuleType.NAMING_CONVENTION, RuleType.ANNOTATION_CHECK) }
        if (relevant.isEmpty()) return 100.0

        // Строим только те правила, которые удалось сконвертировать
        val buildable = relevant.mapNotNull { rule ->
            ArchUnitRuleBuilder.build(rule)?.let { rule to it }
        }
        if (buildable.isEmpty()) return 100.0

        var passed = 0
        for ((rule, archRule) in buildable) {
            try {
                archRule.check(importedClasses)
                passed++
            } catch (e: AssertionError) {
                // правило не выполнено – ничего не делаем
            } catch (e: Exception) {
                logger.warn("Pattern check failed for rule ${rule.id}: ${e.message}")
            }
        }
        return (passed.toDouble() / buildable.size) * 100.0
    }

    fun calculatePatternMatch(classesDir: Path, rules: List<ArchitecturalRule>): Double {
        val imported = ClassFileImporter().importPath(classesDir)
        return calculatePatternMatch(imported, rules)
    }

    fun checkWithViolations(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): List<Violation> {
        val relevant = rules.filter { it.enabled && it.type in listOf(RuleType.NAMING_CONVENTION, RuleType.ANNOTATION_CHECK) }
        val violations = mutableListOf<Violation>()

        for (rule in relevant) {
            val archRule = ArchUnitRuleBuilder.build(rule)
            if (archRule == null) {
                logger.warn("Cannot build ArchRule for ${rule.id} (unsupported constraint?)")
                continue // исключаем из проверки
            }
            try {
                archRule.check(importedClasses)
            } catch (e: AssertionError) {
                val violatingClasses = extractViolatingClasses(e.message, importedClasses, rule)
                if (violatingClasses.isNotEmpty()) {
                    violations.addAll(
                        violatingClasses.map { className ->
                            Violation(
                                ruleId = rule.id,
                                description = formatViolationMessage(rule, className),
                                className = className,
                                severity = rule.severity
                            )
                        }
                    )
                } else {
                    // fallback – общее нарушение
                    violations.add(
                        Violation(
                            ruleId = rule.id,
                            description = formatViolationMessage(rule, e.message ?: "Rule violated"),
                            className = "*",
                            severity = rule.severity
                        )
                    )
                }
            } catch (e: Exception) {
                logger.warn("Check failed for rule ${rule.id}: ${e.message}", e)
                violations.add(
                    Violation(
                        ruleId = rule.id,
                        description = "Check failed: ${e.message}",
                        className = "*",
                        severity = Severity.ERROR
                    )
                )
            }
        }
        return violations
    }

    fun checkWithViolations(classesDir: Path, rules: List<ArchitecturalRule>): List<Violation> {
        val imported = ClassFileImporter().importPath(classesDir)
        return checkWithViolations(imported, rules)
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    /**
     * Извлекает имена классов из сообщения ArchUnit (формат "Class <...>").
     */
    private fun extractViolatingClasses(errorMessage: String?, importedClasses: JavaClasses, rule: ArchitecturalRule): List<String> {
        if (errorMessage == null) return emptyList()
        val pattern = Regex("""Class <([^>]+)>""")
        return pattern.findAll(errorMessage)
            .map { it.groupValues[1] }
            .filter { className -> importedClasses.any { it.name == className } }
            .toList()
    }

    /**
     * Форматирует человекочитаемое сообщение о нарушении.
     */
    private fun formatViolationMessage(rule: ArchitecturalRule, detail: String): String {
        // Если detail – это имя класса, формируем специфическое сообщение
        if (!detail.contains(" ")) {
            return when (rule.constraint) {
                ConstraintType.NAMING_SUFFIX -> "Class `$detail` should end with `${rule.pattern}`"
                ConstraintType.NAMING_PREFIX -> "Class `$detail` should start with `${rule.pattern}`"
                ConstraintType.HAS_ANNOTATION -> "Class `$detail` should have annotation `${rule.annotation}`"
                ConstraintType.NO_ANNOTATION -> "Class `$detail` should not have annotation `${rule.annotation}`"
                else -> "Rule '${rule.name}' violated by class `$detail`"
            }
        }
        // fallback – возвращаем исходное сообщение или краткое описание
        return detail.takeIf { it.isNotBlank() } ?: "Rule '${rule.name}' violated"
    }
}