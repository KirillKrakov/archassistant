package com.example.archassistant.util

import com.example.archassistant.model.*
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import java.nio.file.Path

object PatternMatcher {

    private val logger = LoggerFactory.getLogger(PatternMatcher::class.java)

    fun calculatePatternMatch(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): Double {
        val relevant = rules.filter { it.enabled && it.type in listOf(RuleType.NAMING_CONVENTION, RuleType.ANNOTATION_CHECK) }
        if (relevant.isEmpty()) return 100.0

        var passed = 0
        var evaluated = 0

        for (rule in relevant) {
            val archRule = ArchUnitRuleBuilder.build(rule)
            if (archRule == null) {
                logger.warn("Cannot build ArchRule for ${rule.id} (unsupported constraint?)")
                continue
            }

            try {
                archRule.check(importedClasses)
                passed++
                evaluated++
            } catch (e: AssertionError) {
                if (ArchUnitValidationUtils.isNoApplicableClassesFailure(e.message)) {
                    logger.debug("Skipping pattern rule {}: no applicable generated classes", rule.id)
                    continue
                }
                evaluated++
            } catch (e: Exception) {
                logger.warn("Pattern check failed for rule ${rule.id}: ${e.message}")
                evaluated++
            }
        }

        if (evaluated == 0) return 100.0
        return (passed.toDouble() / evaluated) * 100.0
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
                continue
            }

            try {
                archRule.check(importedClasses)
            } catch (e: AssertionError) {
                if (ArchUnitValidationUtils.isNoApplicableClassesFailure(e.message)) {
                    logger.debug("Skipping pattern rule {} in violations: no applicable generated classes", rule.id)
                    continue
                }

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

    private fun extractViolatingClasses(errorMessage: String?, importedClasses: JavaClasses, rule: ArchitecturalRule): List<String> {
        if (errorMessage == null) return emptyList()
        val pattern = Regex("""Class <([^>]+)>""")
        return pattern.findAll(errorMessage)
            .map { it.groupValues[1] }
            .filter { className -> importedClasses.any { it.name == className } }
            .toList()
    }

    private fun formatViolationMessage(rule: ArchitecturalRule, detail: String): String {
        if (!detail.contains(" ")) {
            return when (rule.constraint) {
                ConstraintType.NAMING_SUFFIX -> "Class `$detail` should end with `${rule.pattern}`"
                ConstraintType.NAMING_PREFIX -> "Class `$detail` should start with `${rule.pattern}`"
                ConstraintType.HAS_ANNOTATION -> "Class `$detail` should have annotation `${rule.annotation}`"
                ConstraintType.NO_ANNOTATION -> "Class `$detail` should not have annotation `${rule.annotation}`"
                else -> "Rule '${rule.name}' violated by class `$detail`"
            }
        }
        return detail.takeIf { it.isNotBlank() } ?: "Rule '${rule.name}' violated"
    }
}