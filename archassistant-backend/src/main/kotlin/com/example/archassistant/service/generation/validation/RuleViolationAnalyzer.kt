package com.example.archassistant.service.generation.validation

import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.model.rules.RuleType
import com.example.archassistant.model.core.Severity
import com.example.archassistant.model.core.Violation
import com.example.archassistant.util.archunit.ArchUnitRuleBuilder
import com.example.archassistant.util.archunit.ArchUnitValidationUtils
import com.tngtech.archunit.core.domain.JavaClasses
import org.slf4j.LoggerFactory

object RuleViolationAnalyzer {

    private val logger = LoggerFactory.getLogger(RuleViolationAnalyzer::class.java)

    fun evaluate(
        importedClasses: JavaClasses,
        rules: List<ArchitecturalRule>,
        acceptedTypes: Set<RuleType> = RuleType.entries.toSet()
    ): RuleEvaluationReport {
        val relevant = rules.filter { it.enabled && it.type in acceptedTypes }
        if (relevant.isEmpty()) return RuleEvaluationReport(passed = 0, evaluated = 0, violations = emptyList())

        var passed = 0
        var evaluated = 0
        val violations = mutableListOf<Violation>()

        for (rule in relevant) {
            val archRule = try {
                ArchUnitRuleBuilder.build(rule)
            } catch (e: Exception) {
                logger.warn("Failed to build rule ${rule.id}: ${e.message}", e)
                null
            } ?: continue

            try {
                archRule.check(importedClasses)
                passed++
                evaluated++
            } catch (e: AssertionError) {
                if (ArchUnitValidationUtils.isNoApplicableClassesFailure(e.message)) {
                    logger.debug("Skipping rule {}: no applicable generated classes", rule.id)
                    continue
                }

                evaluated++
                violations.add(
                    Violation(
                        ruleId = rule.id,
                        description = e.message ?: "Rule violated: ${rule.name}",
                        className = "*",
                        severity = rule.severity
                    )
                )
            } catch (e: Exception) {
                evaluated++
                logger.warn("Rule check failed for ${rule.id}: ${e.message}", e)
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

        return RuleEvaluationReport(
            passed = passed,
            evaluated = evaluated,
            violations = violations
        )
    }

    fun combineViolations(
        importedClasses: JavaClasses,
        rules: List<ArchitecturalRule>,
        acceptedTypes: Set<RuleType> = RuleType.entries.toSet()
    ): List<Violation> {
        return evaluate(importedClasses, rules, acceptedTypes).violations
    }
}