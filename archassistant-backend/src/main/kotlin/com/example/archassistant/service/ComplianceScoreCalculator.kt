package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.util.*
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class ComplianceScoreCalculator(
    private val codeCompiler: CodeCompiler = CodeCompiler()
) {

    private val logger = LoggerFactory.getLogger(ComplianceScoreCalculator::class.java)

    fun calculate(
        code: String,
        className: String,
        rules: List<ArchitecturalRule>,
        weights: ScoreWeights = ScoreWeights(),
        classpath: String = "",
        projectContext: ProjectContextSnapshot? = null
    ): ComplianceScore {

        var tempRoot: Path? = null

        return try {
            tempRoot = codeCompiler.compileCode(code, className, classpath, projectContext)
            val classesDir = tempRoot.resolve("classes")

            // ТОЛЬКО generated classes
            val importedClasses = ClassFileImporter().importPath(classesDir)

            val rulesPass = calculateRulesPass(importedClasses, rules)
            val patternMatch = PatternMatcher.calculatePatternMatch(importedClasses, rules)
            val dependencyCorrect = DependencyAnalyzer.calculateDependencyCorrect(importedClasses, rules)
            val violations = collectViolations(importedClasses, rules)

            val total = (
                    weights.rulesPass * rulesPass +
                            weights.patternMatch * patternMatch +
                            weights.dependencyCorrect * dependencyCorrect
                    ) / (weights.rulesPass + weights.patternMatch + weights.dependencyCorrect)

            ComplianceScore(
                total = total.coerceIn(0.0, 100.0),
                rulesPass = rulesPass.coerceIn(0.0, 100.0),
                patternMatch = patternMatch.coerceIn(0.0, 100.0),
                dependencyCorrect = dependencyCorrect.coerceIn(0.0, 100.0),
                weights = weights,
                violations = violations
            )
        } catch (e: Exception) {
            logger.error("Score calculation failed: ${e.message}", e)
            ComplianceScore(
                total = 0.0,
                rulesPass = 0.0,
                patternMatch = 0.0,
                dependencyCorrect = 0.0,
                weights = weights,
                violations = listOf(
                    Violation(
                        ruleId = "calculation_error",
                        description = "Failed to calculate score: ${e.message}",
                        className = className,
                        severity = Severity.CRITICAL
                    )
                )
            )
        } finally {
            tempRoot?.let { codeCompiler.cleanup(it) }
        }
    }

    private fun calculateRulesPass(
        importedClasses: JavaClasses,
        rules: List<ArchitecturalRule>
    ): Double {
        val dependencyRules = rules.filter {
            it.enabled && it.type == RuleType.DEPENDENCY
        }

        if (dependencyRules.isEmpty()) return 100.0

        var passedCount = 0
        var evaluatedCount = 0

        for (rule in dependencyRules) {
            val archRule = ArchUnitRuleBuilder.build(rule)
            if (archRule == null) {
                logger.warn("Cannot build rule ${rule.id}: unsupported constraint")
                continue
            }

            try {
                archRule.check(importedClasses)
                passedCount++
                evaluatedCount++
            } catch (e: AssertionError) {
                if (ArchUnitValidationUtils.isNoApplicableClassesFailure(e.message)) {
                    logger.debug("Skipping dependency rule {}: no applicable generated classes", rule.id)
                    continue
                }
                evaluatedCount++
            } catch (e: Exception) {
                logger.warn("Rule check failed for ${rule.id}: ${e.message}")
                evaluatedCount++
            }
        }

        if (evaluatedCount == 0) return 100.0
        return (passedCount.toDouble() / evaluatedCount) * 100.0
    }

    private fun collectViolations(
        importedClasses: JavaClasses,
        rules: List<ArchitecturalRule>
    ): List<Violation> {
        return PatternMatcher.checkWithViolations(importedClasses, rules) +
                DependencyAnalyzer.analyzeWithViolations(importedClasses, rules)
    }

    fun isPassing(
        code: String,
        className: String,
        rules: List<ArchitecturalRule>,
        threshold: Double = 70.0,
        classpath: String = "",
        projectContext: ProjectContextSnapshot? = null
    ): Boolean {
        return calculate(
            code = code,
            className = className,
            rules = rules,
            classpath = classpath,
            projectContext = projectContext
        ).total >= threshold
    }
}