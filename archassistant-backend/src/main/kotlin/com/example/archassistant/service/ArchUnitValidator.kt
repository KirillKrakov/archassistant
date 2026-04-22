package com.example.archassistant.service

import com.example.archassistant.model.Severity
import com.example.archassistant.model.ValidationResult
import com.example.archassistant.model.Violation
import com.example.archassistant.util.CodeCompiler
import com.example.archassistant.util.CompilationException
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.system.measureTimeMillis

@Service
class ArchUnitValidator {
    private val logger = LoggerFactory.getLogger(ArchUnitValidator::class.java)

    // Основной метод валидации с явным именем класса
    fun validate(
        code: String,
        className: String,
        rules: List<ArchRule> = emptyList(),
        classpath: String = ""
    ): ValidationResult {
        var result: ValidationResult
        val executionTime = measureTimeMillis {
            result = validateInternal(code, className, rules, classpath)
        }
        return result.copy(executionTimeMs = executionTime)
    }

    // Перегрузка без имени класса – пытаемся извлечь
    fun validate(
        code: String,
        rules: List<ArchRule> = emptyList(),
        classpath: String = ""
    ): ValidationResult {
        val className = extractClassName(code)
        return validate(code, className, rules, classpath)
    }

    private fun validateInternal(
        code: String,
        className: String,
        rules: List<ArchRule>,
        classpath: String
    ): ValidationResult {
        val compiler = CodeCompiler()
        var tempRoot: Path? = null
        return try {
            tempRoot = compiler.compileCode(code, className, classpath)
            val classesDir = tempRoot.resolve("classes")
            val importedClasses = ClassFileImporter().importPath(classesDir)

            val violations = mutableListOf<Violation>()
            rules.forEachIndexed { idx, rule ->
                try {
                    rule.check(importedClasses)
                } catch (e: AssertionError) {
                    violations.add(Violation(
                        ruleId = "rule_${idx + 1}",
                        description = e.message ?: "Rule violation",
                        className = className,
                        severity = Severity.ERROR
                    ))
                }
            }
            if (violations.isEmpty()) ValidationResult.success()
            else ValidationResult.failure(violations)
        } catch (e: CompilationException) {
            ValidationResult.failure(
                violations = listOf(Violation(
                    ruleId = "compilation_error",
                    description = e.message ?: "Compilation failed",
                    className = className,
                    severity = Severity.CRITICAL
                )),
                message = "Compilation error: ${e.message}"
            )
        } catch (e: Exception) {
            logger.error("Validation error", e)
            ValidationResult.failure(
                violations = listOf(Violation(
                    ruleId = "validation_error",
                    description = e.message ?: "Unknown error",
                    className = className,
                    severity = Severity.CRITICAL
                )),
                message = "Validation error: ${e.message}"
            )
        } finally {
            tempRoot?.let { compiler.cleanup(it) }
        }
    }

    // Упрощённая валидация (только компиляция)
    fun validateBasic(code: String, className: String): ValidationResult =
        validate(code, className, emptyList())

    fun validateBasic(code: String): ValidationResult =
        validate(code, emptyList())

    // Улучшенное извлечение имени класса (поддерживает вложенные и аннотации)
    private fun extractClassName(code: String): String {
        val pattern = Regex("""(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(?:sealed\s+)?class\s+(\w+)""")
        return pattern.find(code)?.groupValues?.get(1)
            ?: code.lines().firstOrNull { it.contains("class ") }
                ?.substringAfter("class ")
                ?.substringBefore(' ')
                ?.substringBefore('{')
                ?.trim()
            ?: "GeneratedClass"
    }

    // Вспомогательные методы для создания правил
    fun noDependencyRule(fromPackage: String, toPackage: String): ArchRule =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that().resideInAPackage(fromPackage)
            .should().dependOnClassesThat()
            .resideInAPackage(toPackage)

    fun namingConventionRule(packagePattern: String, requiredSuffix: String): ArchRule =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
            .that().resideInAPackage(packagePattern)
            .should().haveSimpleNameEndingWith(requiredSuffix)
}