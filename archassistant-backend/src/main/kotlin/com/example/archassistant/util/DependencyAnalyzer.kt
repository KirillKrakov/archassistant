package com.example.archassistant.util

import com.example.archassistant.model.*
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import java.nio.file.Path

object DependencyAnalyzer {

    private val logger = LoggerFactory.getLogger(DependencyAnalyzer::class.java)

    fun calculateDependencyCorrect(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): Double {
        val dependencyRules = rules.filter { it.enabled && it.type == RuleType.DEPENDENCY }
        if (dependencyRules.isEmpty()) return 100.0

        var passed = 0
        var evaluated = 0

        for (rule in dependencyRules) {
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
                    logger.debug("Skipping dependency rule {}: no applicable generated classes", rule.id)
                    continue
                }
                evaluated++
            } catch (e: Exception) {
                logger.warn("Dependency check failed for rule ${rule.id}: ${e.message}")
                evaluated++
            }
        }

        if (evaluated == 0) return 100.0
        return (passed.toDouble() / evaluated) * 100.0
    }

    fun calculateDependencyCorrect(classesDir: Path, rules: List<ArchitecturalRule>): Double {
        val imported = ClassFileImporter().importPath(classesDir)
        return calculateDependencyCorrect(imported, rules)
    }

    fun analyzeWithViolations(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): List<Violation> {
        val dependencyRules = rules.filter { it.enabled && it.type == RuleType.DEPENDENCY }
        val violations = mutableListOf<Violation>()

        for (rule in dependencyRules) {
            val archRule = ArchUnitRuleBuilder.build(rule)
            if (archRule == null) {
                logger.warn("Cannot build ArchRule for ${rule.id}")
                continue
            }

            try {
                archRule.check(importedClasses)
            } catch (e: AssertionError) {
                if (ArchUnitValidationUtils.isNoApplicableClassesFailure(e.message)) {
                    logger.debug("Skipping dependency rule {} in violations: no applicable generated classes", rule.id)
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

    fun analyzeWithViolations(classesDir: Path, rules: List<ArchitecturalRule>): List<Violation> {
        val imported = ClassFileImporter().importPath(classesDir)
        return analyzeWithViolations(imported, rules)
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
                ConstraintType.NO_DEPENDENCY ->
                    "Class `$detail` has forbidden dependency on a class in `${rule.toPackage ?: rule.toPackages?.joinToString(", ")}`"
                ConstraintType.MUST_DEPEND ->
                    "Class `$detail` is missing required dependency on `${rule.toPackage ?: rule.toPackages?.joinToString(", ")}`"
                else -> "Rule '${rule.name}' violated by class `$detail`"
            }
        }
        return detail.takeIf { it.isNotBlank() } ?: "Rule '${rule.name}' violated"
    }
}