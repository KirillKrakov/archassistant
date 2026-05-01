package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.util.ArchUnitRuleBuilder
import com.example.archassistant.util.ClasspathUtils
import com.example.archassistant.util.CodeCompiler
import com.example.archassistant.model.ProjectContextSnapshot
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.system.measureTimeMillis

/**
 * Сервис программной валидации кода через ArchUnit API
 * Не генерирует Java-файлы тестов — правила создаются и проверяются в памяти
 */
@Service
class DynamicRuleValidator(
    private val codeCompiler: CodeCompiler = CodeCompiler()
) {

    private val logger = LoggerFactory.getLogger(DynamicRuleValidator::class.java)

    fun validate(
        code: String,
        className: String,
        rules: List<ArchitecturalRule>,
        classpath: String = "",
        projectContext: ProjectContextSnapshot? = null
    ): ValidationResult {
        var result: ValidationResult
        val executionTime = measureTimeMillis {
            result = validateInternal(code, className, rules, classpath, projectContext)
        }
        return result.copy(executionTimeMs = executionTime)
    }

    /**
     * Упрощённая валидация (только компиляция + базовые проверки)
     */
    fun validateBasic(
        code: String,
        className: String,
        classpath: String = "",
        projectContext: ProjectContextSnapshot? = null
    ): ValidationResult {
        var tempRoot: Path? = null
        var result: ValidationResult
        val executionTime = measureTimeMillis {
            result = try {
                tempRoot = codeCompiler.compileCode(code, className, classpath, projectContext)
                ValidationResult.success("Code compiled successfully")
            } catch (e: Exception) {
                ValidationResult.failure(
                    violations = listOf(
                        Violation(
                            ruleId = "compilation",
                            description = e.message ?: "Compilation failed",
                            className = className,
                            severity = Severity.CRITICAL
                        )
                    )
                )
            } finally {
                tempRoot?.let { codeCompiler.cleanup(it) }
            }
        }
        return result.copy(executionTimeMs = executionTime)
    }

    private fun validateInternal(
        code: String,
        className: String,
        rules: List<ArchitecturalRule>,
        classpath: String,
        projectContext: ProjectContextSnapshot?
    ): ValidationResult {
        var tempRoot: Path? = null

        return try {
            tempRoot = codeCompiler.compileCode(code, className, classpath, projectContext)
            val classesDir = tempRoot.resolve("classes")

            val importPaths = linkedSetOf<Path>().apply {
                add(classesDir)
                addAll(projectContext?.importPaths().orEmpty())
                addAll(ClasspathUtils.splitClasspathToDirectories(classpath))
            }

            val importedClasses: JavaClasses =
                ClassFileImporter().importPaths(*importPaths.toTypedArray())

            val enabledRules = rules.filter { it.enabled }

            val validPairs = enabledRules.mapNotNull { rule ->
                try {
                    ArchUnitRuleBuilder.build(rule)?.let { archRule -> rule to archRule }
                } catch (e: Exception) {
                    logger.warn("Failed to build rule ${rule.id}: ${e.message}")
                    null
                }
            }

            val violations = mutableListOf<Violation>()

            for ((rule, archRule) in validPairs) {
                try {
                    archRule.check(importedClasses)
                } catch (e: AssertionError) {
                    violations.add(
                        Violation(
                            ruleId = rule.id,
                            description = e.message ?: "Rule violation: ${rule.name}",
                            className = className,
                            severity = rule.severity
                        )
                    )
                }
            }

            if (violations.isEmpty()) {
                ValidationResult.success("All ${validPairs.size} rules passed")
            } else {
                ValidationResult.failure(violations)
            }
        } catch (e: Exception) {
            logger.error("Validation failed: ${e.message}", e)
            ValidationResult.failure(
                violations = listOf(
                    Violation(
                        ruleId = "validation_error",
                        description = e.message ?: "Unknown validation error",
                        className = className,
                        severity = Severity.CRITICAL
                    )
                ),
                message = "Validation error: ${e.message}"
            )
        } finally {
            tempRoot?.let { codeCompiler.cleanup(it) }
        }
    }

    fun validateSingleRule(
        code: String,
        className: String,
        rule: ArchitecturalRule,
        classpath: String = "",
        projectContext: ProjectContextSnapshot? = null
    ): ValidationResult {
        return validate(code, className, listOf(rule), classpath, projectContext)
    }

    fun validateBatch(
        codeSnippets: List<Pair<String, String>>,
        rules: List<ArchitecturalRule>,
        classpath: String = "",
        projectContext: ProjectContextSnapshot? = null
    ): Map<String, ValidationResult> {
        return codeSnippets.associate { (code, className) ->
            className to validate(code, className, rules, classpath, projectContext)
        }
    }
}