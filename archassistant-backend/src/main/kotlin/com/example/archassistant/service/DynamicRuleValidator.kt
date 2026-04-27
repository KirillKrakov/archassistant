package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.util.ArchUnitRuleBuilder
import com.example.archassistant.util.CodeCompiler
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

    /**
     * Валидация исходного кода против списка архитектурных правил
     *
     * @param code Исходный код для валидации
     * @param className Имя класса (для компиляции)
     * @param rules Список правил для проверки
     * @param classpath Дополнительный classpath для компиляции
     * @return ValidationResult с результатом валидации
     */
    fun validate(
        code: String,
        className: String,
        rules: List<ArchitecturalRule>,
        classpath: String = ""
    ): ValidationResult {

        var result: ValidationResult
        val executionTime = measureTimeMillis {
            result = validateInternal(code, className, rules, classpath)
        }
        return result.copy(executionTimeMs = executionTime)
    }

    /**
     * Упрощённая валидация (только компиляция + базовые проверки)
     */
    fun validateBasic(code: String, className: String): ValidationResult {
        var tempRoot: Path? = null
        var result: ValidationResult
        val executionTime = measureTimeMillis {
            result = try {
                tempRoot = codeCompiler.compileCode(code, className)
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

    /**
     * Внутренняя реализация валидации
     */
    private fun validateInternal(
        code: String,
        className: String,
        rules: List<ArchitecturalRule>,
        classpath: String
    ): ValidationResult {

        var tempRoot: Path? = null

        return try {
            // Шаг 1: Компиляция кода
            tempRoot = codeCompiler.compileCode(code, className, classpath)
            val classesDir = tempRoot.resolve("classes")

            // Шаг 2: Импорт классов в ArchUnit
            val importedClasses: JavaClasses = ClassFileImporter().importPath(classesDir)

            // Шаг 3: Фильтрация и преобразование правил
            val enabledRules = rules.filter { it.enabled }

            // Создаём пары (rule, archRule) только для успешно сконвертированных
            // Это предотвращает смещение индексов после mapNotNull
            val validPairs = enabledRules.mapNotNull { rule ->
                try {
                    ArchUnitRuleBuilder.build(rule)?.let { archRule -> rule to archRule }
                } catch (e: Exception) {
                    logger.warn("Failed to build rule ${rule.id}: ${e.message}")
                    null
                }
            }

            // Шаг 4: Применение правил
            val violations = mutableListOf<Violation>()

            for ((rule, archRule) in validPairs) {
                try {
                    archRule.check(importedClasses)
                } catch (e: AssertionError) {
                    violations.add(
                        Violation(
                            ruleId = rule.id,  // берём ID из оригинального rule, не по индексу
                            description = e.message ?: "Rule violation: ${rule.name}",
                            className = className,
                            severity = rule.severity
                        )
                    )
                }
            }

            // Шаг 5: Возврат результата
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
            // Шаг 6: Очистка временных файлов
            tempRoot?.let { codeCompiler.cleanup(it) }
        }
    }

    /**
     * Проверка одного правила (для тестирования)
     */
    fun validateSingleRule(
        code: String,
        className: String,
        rule: ArchitecturalRule,
        classpath: String = ""
    ): ValidationResult {
        return validate(code, className, listOf(rule), classpath)
    }

    /**
     * Пакетная валидация нескольких фрагментов кода
     */
    fun validateBatch(
        codeSnippets: List<Pair<String, String>>, // (code, className)
        rules: List<ArchitecturalRule>,
        classpath: String = ""
    ): Map<String, ValidationResult> {
        return codeSnippets.associate { (code, className) ->
            className to validate(code, className, rules, classpath)
        }
    }
}