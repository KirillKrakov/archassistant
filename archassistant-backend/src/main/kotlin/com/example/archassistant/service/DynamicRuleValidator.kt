package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.model.ProjectContextSnapshot
import com.example.archassistant.util.ArchUnitRuleBuilder
import com.example.archassistant.util.ArchUnitValidationUtils
import com.example.archassistant.util.CodeCompiler
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

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

            val importedClasses: JavaClasses = importCompiledClasses(classesDir, classpath)
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
            var evaluatedRules = 0

            for ((rule, archRule) in validPairs) {
                try {
                    archRule.check(importedClasses)
                    evaluatedRules++
                } catch (e: AssertionError) {
                    if (ArchUnitValidationUtils.isNoApplicableClassesFailure(e.message)) {
                        logger.debug("Skipping rule {}: no applicable generated classes", rule.id)
                        continue
                    }

                    evaluatedRules++
                    violations.add(
                        Violation(
                            ruleId = rule.id,
                            description = e.message ?: "Rule violation: ${rule.name}",
                            className = className,
                            severity = rule.severity
                        )
                    )
                } catch (e: Exception) {
                    evaluatedRules++
                    logger.warn("Validation failed for rule ${rule.id}: ${e.message}", e)
                    violations.add(
                        Violation(
                            ruleId = rule.id,
                            description = "Check failed: ${e.message}",
                            className = className,
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            if (violations.isEmpty()) {
                val message = if (evaluatedRules == 0) {
                    "No applicable rules for generated code"
                } else {
                    "All $evaluatedRules applicable rules passed"
                }
                ValidationResult.success(message)
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

    private fun importCompiledClasses(classesDir: Path, classpath: String): JavaClasses {
        val importUrls = linkedSetOf<URL>()
        importUrls += classesDir.toUri().toURL()
        importUrls += resolveClasspathUrls(classpath)

        return ClassFileImporter().importUrls(importUrls.toList())
    }

    private fun resolveClasspathUrls(classpath: String): List<URL> {
        if (classpath.isBlank()) return emptyList()

        return classpath
            .split(File.pathSeparator)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                runCatching {
                    Paths.get(entry).toUri().toURL()
                }.getOrElse {
                    logger.warn("Skipping invalid classpath entry for ArchUnit import: {}", entry)
                    null
                }
            }
            .toList()
    }
}