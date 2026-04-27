package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.util.*
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path

@Service
class ComplianceScoreCalculator(
    private val codeCompiler: CodeCompiler = CodeCompiler()
) {

    private val logger = LoggerFactory.getLogger(ComplianceScoreCalculator::class.java)

    /**
     * Расчёт ComplianceScore для исходного кода
     *
     * Формула: (W₁×RulesPass + W₂×PatternMatch + W₃×DependencyCorrect) / (W₁+W₂+W₃)
     */
    fun calculate(
        code: String,
        className: String,
        rules: List<ArchitecturalRule>,
        weights: ScoreWeights = ScoreWeights(),
        classpath: String = ""
    ): ComplianceScore {

        var tempRoot: Path? = null

        return try {
            // Шаг 1: Компиляция и импорт классов (ОДИН РАЗ)
            tempRoot = codeCompiler.compileCode(code, className, classpath)
            val classesDir = tempRoot.resolve("classes")
            val allPaths = mutableListOf(classesDir)
            if (classpath.isNotBlank()) {
                classpath.split(File.pathSeparator).forEach { path ->
                    if (path.isNotBlank()) {
                        val p = Path.of(path)
                        if (p !in allPaths) allPaths.add(p)
                    }
                }
            }
            val importedClasses = ClassFileImporter().importPaths(*allPaths.toTypedArray())

            // Шаг 2: Расчёт компонентов с переиспользованием importedClasses
            val rulesPass = calculateRulesPass(importedClasses, rules)
            val patternMatch = PatternMatcher.calculatePatternMatch(importedClasses, rules)
            val dependencyCorrect = DependencyAnalyzer.calculateDependencyCorrect(importedClasses, rules)

            // Шаг 3: Сбор нарушений
            val violations = collectViolations(importedClasses, rules)

            // Шаг 4: Итоговый расчёт
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

    /**
     * Расчёт RulesPass: % правил типа DEPENDENCY, которые прошли проверку
     * FIXED: принимает JavaClasses, не вызывает повторную компиляцию
     */
    private fun calculateRulesPass(
        importedClasses: JavaClasses,
        rules: List<ArchitecturalRule>
    ): Double {
        val dependencyRules = rules.filter {
            it.enabled && it.type == RuleType.DEPENDENCY
        }

        if (dependencyRules.isEmpty()) return 100.0

        // FIXED: считаем количество ПРАВИЛ, которые прошли (не нарушений)
        val passedCount = dependencyRules.count { rule ->
            try {
                val archRule = ArchUnitRuleBuilder.build(rule)
                if (archRule != null) {
                    archRule.check(importedClasses)
                    true  // Правило прошло
                } else {
                    false  // Правило не удалось построить
                }
            } catch (e: AssertionError) {
                false  // Правило нарушено
            } catch (e: Exception) {
                logger.warn("Rule check failed for ${rule.id}: ${e.message}")
                false  // Ошибка проверки = нарушение
            }
        }

        return (passedCount.toDouble() / dependencyRules.size) * 100.0
    }

    /**
     * Сбор всех нарушений для детализации
     */
    private fun collectViolations(
        importedClasses: JavaClasses,
        rules: List<ArchitecturalRule>
    ): List<Violation> {
        return PatternMatcher.checkWithViolations(importedClasses, rules) +
                DependencyAnalyzer.analyzeWithViolations(importedClasses, rules)
    }

    /**
     * Быстрая проверка: проходит ли код порог качества
     */
    fun isPassing(
        code: String,
        className: String,
        rules: List<ArchitecturalRule>,
        threshold: Double = 70.0,
        classpath: String = ""
    ): Boolean {
        return calculate(code, className, rules, classpath = classpath).total >= threshold
    }

    /**
     * @Deprecated: Используйте calculate() с компиляцией.
     * Этот метод оставлен только для обратной совместимости с тестами.
     */
    @Deprecated("Use calculate() with actual compilation; kept for legacy tests only")
    fun calculateForCompiled(
        classesDir: Path,
        rules: List<ArchitecturalRule>,
        weights: ScoreWeights = ScoreWeights()
    ): ComplianceScore {
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

        return ComplianceScore(
            total = total.coerceIn(0.0, 100.0),
            rulesPass = rulesPass.coerceIn(0.0, 100.0),
            patternMatch = patternMatch.coerceIn(0.0, 100.0),
            dependencyCorrect = dependencyCorrect.coerceIn(0.0, 100.0),
            weights = weights,
            violations = violations)
    }
}