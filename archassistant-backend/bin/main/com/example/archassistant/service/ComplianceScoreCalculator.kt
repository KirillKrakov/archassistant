package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.util.*
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
            val importedClasses = ClassFileImporter().importPath(classesDir)

            val allRules = rules.filter { it.enabled }
            val universal = RuleViolationAnalyzer.evaluate(importedClasses, allRules, RuleType.entries.toSet())

            val pattern = RuleViolationAnalyzer.evaluate(
                importedClasses,
                allRules,
                setOf(
                    RuleType.NAMING_CONVENTION,
                    RuleType.ANNOTATION_CHECK,
                    RuleType.MODIFIER_CHECK,
                    RuleType.METHOD_SIGNATURE_CHECK,
                    RuleType.FIELD_CHECK,
                    RuleType.EXCEPTION_CHECK
                )
            )

            val dependency = RuleViolationAnalyzer.evaluate(
                importedClasses,
                allRules,
                setOf(
                    RuleType.DEPENDENCY,
                    RuleType.LAYER_ISOLATION,
                    RuleType.CYCLE_CHECK,
                    RuleType.INHERITANCE_CHECK,
                    RuleType.INTERFACE_CHECK
                )
            )

            val violations = universal.violations.ifEmpty {
                pattern.violations + dependency.violations
            }

            val total = (
                    weights.rulesPass * universal.score +
                            weights.patternMatch * pattern.score +
                            weights.dependencyCorrect * dependency.score
                    ) / (weights.rulesPass + weights.patternMatch + weights.dependencyCorrect)

            ComplianceScore(
                total = total.coerceIn(0.0, 100.0),
                rulesPass = universal.score.coerceIn(0.0, 100.0),
                patternMatch = pattern.score.coerceIn(0.0, 100.0),
                dependencyCorrect = dependency.score.coerceIn(0.0, 100.0),
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