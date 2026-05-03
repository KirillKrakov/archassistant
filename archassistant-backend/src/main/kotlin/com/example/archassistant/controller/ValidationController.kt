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
import com.example.archassistant.dto.RuleDefinition
import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.ConstraintType
import com.example.archassistant.model.RuleType
import com.example.archassistant.model.SelectorMode

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

    fun convert(input: RuleDefinition): ArchitecturalRule {
        val type = convertType(input.type)
        val constraint = input.constraint?.let { convertConstraint(it) } ?: defaultConstraintFor(type)

        return ArchitecturalRule(
            id = input.id?.takeIf { it.isNotBlank() } ?: "temp_${java.util.UUID.randomUUID()}",
            name = input.name ?: "Converted rule from ${input.fromPackage}",
            description = input.description ?: "Rule converted from API request",
            type = type,
            fromPackage = normalizeReferenceValue(input.fromPackage) ?: input.fromPackage,
            toPackage = normalizeReferenceValue(input.toPackage),
            toPackages = input.toPackages?.mapNotNull { normalizeReferenceValue(it) },
            constraint = constraint,
            pattern = input.pattern,
            annotation = input.annotation,
            fromSelectorMode = convertSelectorMode(input.fromSelectorMode) ?: SelectorMode.PACKAGE,
            toSelectorMode = convertSelectorMode(input.toSelectorMode) ?: SelectorMode.PACKAGE,
            fromClassType = convertClassType(input.fromClassType),
            toClassType = convertClassType(input.toClassType),
            fromLayerType = convertLayerType(input.fromLayerType),
            toLayerType = convertLayerType(input.toLayerType),
            fromNamePattern = input.fromNamePattern,
            toNamePattern = input.toNamePattern,
            fromMethodNamePattern = input.fromMethodNamePattern,
            toMethodNamePattern = input.toMethodNamePattern,
            fromFieldNamePattern = input.fromFieldNamePattern,
            toFieldNamePattern = input.toFieldNamePattern,
            fromReturnType = input.fromReturnType,
            toReturnType = input.toReturnType,
            fromParameterTypes = input.fromParameterTypes,
            toParameterTypes = input.toParameterTypes,
            fromThrowsTypes = input.fromThrowsTypes,
            toThrowsTypes = input.toThrowsTypes,
            fromModifiers = input.fromModifiers,
            toModifiers = input.toModifiers,
            fromFieldType = input.fromFieldType,
            toFieldType = input.toFieldType,
            slicePattern = input.slicePattern,
            maxCycleLength = input.maxCycleLength,
            severity = convertSeverity(input.severity),
            weight = input.weight ?: 1.0,
            enabled = input.enabled,
            suggested = false
        )
    }

    private fun normalizeReferenceValue(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val value = raw.trim()
        val simple = value.substringAfterLast('.')
        val looksLikeTypeName =
            simple.isNotBlank() &&
                    simple.firstOrNull()?.isUpperCase() == true &&
                    !value.contains('*') &&
                    !value.contains("..")

        return if (looksLikeTypeName) {
            value
        } else {
            PackagePatternBuilder.buildWildcardPatterns(listOf(value)).firstOrNull() ?: value
        }
    }

    private fun convertType(typeString: String): RuleType {
        return runCatching { RuleType.fromValue(typeString) }.getOrDefault(RuleType.DEPENDENCY)
    }

    private fun convertConstraint(constraintString: String): ConstraintType {
        return runCatching { ConstraintType.fromValue(constraintString) }.getOrDefault(ConstraintType.NO_DEPENDENCY)
    }

    private fun convertSelectorMode(value: String?): SelectorMode? {
        if (value.isNullOrBlank()) return null
        return runCatching { SelectorMode.valueOf(value.trim().uppercase()) }.getOrNull()
    }

    private fun convertClassType(value: String?): com.example.archassistant.model.ClassType? {
        if (value.isNullOrBlank()) return null
        return runCatching { com.example.archassistant.model.ClassType.valueOf(value.trim().uppercase()) }.getOrNull()
    }

    private fun convertLayerType(value: String?): com.example.archassistant.model.LayerType? {
        if (value.isNullOrBlank()) return null
        return runCatching { com.example.archassistant.model.LayerType.valueOf(value.trim().uppercase()) }.getOrNull()
    }

    private fun convertSeverity(value: String): Severity {
        return runCatching { Severity.valueOf(value.trim().uppercase()) }.getOrDefault(Severity.INFO)
    }

    private fun defaultConstraintFor(type: RuleType): ConstraintType {
        return when (type) {
            RuleType.DEPENDENCY, RuleType.LAYER_ISOLATION -> ConstraintType.NO_DEPENDENCY
            RuleType.NAMING_CONVENTION -> ConstraintType.NAMING_SUFFIX
            RuleType.ANNOTATION_CHECK -> ConstraintType.HAS_ANNOTATION
            RuleType.CYCLE_CHECK -> ConstraintType.NO_CYCLE
            RuleType.INHERITANCE_CHECK -> ConstraintType.SHOULD_EXTEND
            RuleType.INTERFACE_CHECK -> ConstraintType.SHOULD_IMPLEMENT
            RuleType.MODIFIER_CHECK -> ConstraintType.SHOULD_BE_PUBLIC
            RuleType.METHOD_SIGNATURE_CHECK -> ConstraintType.METHOD_NAME_PATTERN
            RuleType.FIELD_CHECK -> ConstraintType.FIELD_NAME_PATTERN
            RuleType.EXCEPTION_CHECK -> ConstraintType.SHOULD_ONLY_THROW
            RuleType.CUSTOM -> ConstraintType.CUSTOM
        }
    }
}