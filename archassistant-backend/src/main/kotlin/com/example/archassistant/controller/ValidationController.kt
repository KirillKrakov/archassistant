package com.example.archassistant.controller

import com.example.archassistant.dto.ValidationRequest
import com.example.archassistant.dto.ValidationResponse
import com.example.archassistant.model.Severity
import com.example.archassistant.model.ValidationResult
import com.example.archassistant.model.Violation
import com.example.archassistant.service.DynamicRuleValidator
import com.example.archassistant.service.ProjectContextService
import com.example.archassistant.service.YamlRuleRepository
import com.example.archassistant.util.PackagePatternBuilder
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/validate")
@CrossOrigin(origins = ["*"])
class ValidationController(
    private val validator: DynamicRuleValidator,
    private val ruleRepository: YamlRuleRepository,
    private val projectContextService: ProjectContextService
) {

    private val logger = LoggerFactory.getLogger(ValidationController::class.java)

    /**
     * Валидировать код против правил проекта
     */
    @PostMapping
    fun validateCode(@RequestBody request: ValidationRequest): ResponseEntity<ValidationResponse> {
        logger.info(
            "Received validation request: className={}, projectId={}",
            request.className, request.projectId
        )

        return try {
            val projectContext = resolveProjectContext(request.projectId)

            // 1. Загружаем правила: явные > из projectId > пустой список
            val rules = when {
                request.rules != null -> {
                    request.rules.map { RuleConverter.convert(it) }
                }
                request.projectId != null -> {
                    ruleRepository.load(request.projectId)?.getEnabledRules() ?: emptyList()
                }
                else -> emptyList()
            }

            // 2. Определяем имя класса: явное > извлечение из кода > ошибка
            val className = request.className ?: extractClassName(request.code)
            ?: return ResponseEntity.badRequest().body(
                ValidationResponse(
                    ValidationResult.failure(
                        violations = listOf(
                            Violation(
                                ruleId = "validation_error",
                                description = "Could not determine class name. Specify className in request or ensure code contains a class declaration.",
                                className = "Unknown",
                                severity = Severity.CRITICAL
                            )
                        )
                    )
                )
            )

            // 3. Выполняем валидацию с projectContext
            val result = validator.validate(
                code = request.code,
                className = className,
                rules = rules,
                classpath = request.classpath ?: "",
                projectContext = projectContext
            )

            logger.info(
                "Validation completed: passed={}, violations={}",
                result.passed, result.violations.size
            )

            ResponseEntity.ok(ValidationResponse(result))

        } catch (e: Exception) {
            logger.error("Validation failed: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                ValidationResponse(
                    ValidationResult.failure(
                        violations = listOf(
                            Violation(
                                ruleId = "validation_error",
                                description = "Validation failed: ${e.message}",
                                className = request.className ?: "Unknown",
                                severity = Severity.CRITICAL
                            )
                        )
                    )
                )
            )
        }
    }

    /**
     * Быстрая валидация (только компиляция, без правил)
     */
    @PostMapping("/basic")
    fun validateBasic(@RequestBody request: ValidationRequest): ResponseEntity<ValidationResponse> {
        logger.info("Received basic validation request: className={}", request.className)

        return try {
            val projectContext = resolveProjectContext(request.projectId)

            val className = request.className ?: extractClassName(request.code)
            ?: return ResponseEntity.badRequest().body(
                ValidationResponse(
                    ValidationResult.failure(
                        violations = listOf(
                            Violation(
                                ruleId = "compilation_error",
                                description = "Could not determine class name for basic validation",
                                className = "Unknown",
                                severity = Severity.CRITICAL
                            )
                        )
                    )
                )
            )

            val result = validator.validateBasic(
                code = request.code,
                className = className,
                classpath = request.classpath ?: "",
                projectContext = projectContext
            )

            ResponseEntity.ok(ValidationResponse(result))

        } catch (e: Exception) {
            logger.error("Basic validation failed: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                ValidationResponse(
                    ValidationResult.failure(
                        violations = listOf(
                            Violation(
                                ruleId = "compilation_error",
                                description = "Compilation failed: ${e.message}",
                                className = request.className ?: "Unknown",
                                severity = Severity.CRITICAL
                            )
                        )
                    )
                )
            )
        }
    }

    /**
     * Загружает ProjectContextSnapshot по projectId.
     * Если projectId передан, но контекст не собран — лучше остановить валидацию сразу,
     * чем компилировать код в пустом classpath и получать ложную ошибку.
     */
    private fun resolveProjectContext(projectId: String?): com.example.archassistant.model.ProjectContextSnapshot? {
        val trimmed = projectId?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        return projectContextService.getProjectContext(trimmed) ?: throw IllegalStateException(
            "Project context is unavailable for projectId='$trimmed'. " +
                    "Set projectPath and ensure project classes are built before validation."
        )
    }

    /**
     * Извлечение имени класса из исходного кода
     */
    private fun extractClassName(code: String): String? {
        val pattern = Regex("""(?:public\s+)?(?:private\s+)?(?:protected\s+)?(?:abstract\s+)?(?:final\s+)?(?:sealed\s+)?(?:data\s+)?class\s+(\w+)""")

        return pattern.find(code)?.groupValues?.get(1)
            ?: code.lines()
                .firstOrNull { line ->
                    line.contains("class ") &&
                            !line.trimStart().startsWith("//") &&
                            !line.trimStart().startsWith("/*")
                }
                ?.substringAfter("class ")
                ?.substringBefore(' ')
                ?.substringBefore('{')
                ?.takeIf { it.isNotBlank() && it !in listOf("class", "data", "sealed", "abstract") }
    }
}

/**
 * Конвертер RuleDefinition → ArchitecturalRule
 */
object RuleConverter {

    fun convert(input: com.example.archassistant.dto.RuleDefinition): com.example.archassistant.model.ArchitecturalRule {
        val type = convertType(input.type)
        val constraint = input.constraint?.let { convertConstraint(it) } ?: defaultConstraintFor(type)

        return com.example.archassistant.model.ArchitecturalRule(
            id = "temp_${java.util.UUID.randomUUID()}",
            name = "Converted rule from ${input.fromPackage}",
            description = "Rule converted from API request",
            type = type,
            fromPackage = normalizePackagePattern(input.fromPackage),
            toPackage = input.toPackage?.let { normalizePackagePattern(it) },
            toPackages = input.toPackages?.map { normalizePackagePattern(it) },
            constraint = constraint,
            severity = com.example.archassistant.model.Severity.WARNING,
            weight = 1.0,
            enabled = true,
            suggested = false
        )
    }

    private fun convertType(typeString: String): com.example.archassistant.model.RuleType {
        return try {
            com.example.archassistant.model.RuleType.valueOf(typeString.uppercase())
        } catch (e: IllegalArgumentException) {
            com.example.archassistant.model.RuleType.DEPENDENCY
        }
    }

    private fun convertConstraint(constraintString: String): com.example.archassistant.model.ConstraintType {
        return try {
            com.example.archassistant.model.ConstraintType.valueOf(
                constraintString.uppercase().replace(" ", "_")
            )
        } catch (e: IllegalArgumentException) {
            com.example.archassistant.model.ConstraintType.NO_DEPENDENCY
        }
    }

    private fun defaultConstraintFor(type: com.example.archassistant.model.RuleType): com.example.archassistant.model.ConstraintType {
        return when (type) {
            com.example.archassistant.model.RuleType.DEPENDENCY -> com.example.archassistant.model.ConstraintType.NO_DEPENDENCY
            com.example.archassistant.model.RuleType.NAMING_CONVENTION -> com.example.archassistant.model.ConstraintType.NAMING_SUFFIX
            com.example.archassistant.model.RuleType.ANNOTATION_CHECK -> com.example.archassistant.model.ConstraintType.HAS_ANNOTATION
            else -> com.example.archassistant.model.ConstraintType.NO_DEPENDENCY
        }
    }

    private fun normalizePackagePattern(pattern: String): String {
        return PackagePatternBuilder.buildWildcardPatterns(listOf(pattern)).firstOrNull() ?: pattern
    }
}