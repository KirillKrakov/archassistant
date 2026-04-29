package com.example.archassistant.controller

import com.example.archassistant.dto.ValidationRequest
import com.example.archassistant.dto.ValidationResponse
import com.example.archassistant.model.Violation
import com.example.archassistant.model.ValidationResult
import com.example.archassistant.service.DynamicRuleValidator
import com.example.archassistant.service.YamlRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.example.archassistant.util.PackagePatternBuilder

@RestController
@RequestMapping("/api/validate")
@CrossOrigin(origins = ["*"])
class ValidationController(
    private val validator: DynamicRuleValidator,
    private val ruleRepository: YamlRuleRepository
) {

    private val logger = LoggerFactory.getLogger(ValidationController::class.java)

    /**
     * Валидировать код против правил проекта
     */
    @PostMapping
    fun validateCode(@RequestBody request: ValidationRequest): ResponseEntity<ValidationResponse> {
        logger.info("Received validation request: className={}, projectId={}",
            request.className, request.projectId)

        return try {
            // 1. Загружаем правила: явные > из projectId > пустой список
            val rules = when {
                request.rules != null -> {
                    // Конвертация RuleDefinition → ArchitecturalRule (заглушка, реализовать позже)
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
                                severity = com.example.archassistant.model.Severity.CRITICAL
                            )
                        )
                    )
                )
            )

            // 3. Выполняем валидацию (className теперь гарантированно не null)
            val result = validator.validate(
                code = request.code,
                className = className,
                rules = rules,
                classpath = request.classpath ?: ""
            )

            logger.info("Validation completed: passed={}, violations={}",
                result.passed, result.violations.size)

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
                                severity = com.example.archassistant.model.Severity.CRITICAL
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
            val className = request.className ?: extractClassName(request.code)
            ?: return ResponseEntity.badRequest().body(
                ValidationResponse(
                    ValidationResult.failure(
                        violations = listOf(
                            Violation(
                                ruleId = "compilation_error",
                                description = "Could not determine class name for basic validation",
                                className = "Unknown",
                                severity = com.example.archassistant.model.Severity.CRITICAL
                            )
                        )
                    )
                )
            )

            val result = validator.validateBasic(request.code, className)
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
                                severity = com.example.archassistant.model.Severity.CRITICAL
                            )
                        )
                    )
                )
            )
        }
    }

    /**
     * Извлечение имени класса из исходного кода (вспомогательный метод)
     */
    private fun extractClassName(code: String): String? {
        val pattern = Regex("""(?:public\s+)?(?:private\s+)?(?:protected\s+)?(?:abstract\s+)?(?:final\s+)?(?:sealed\s+)?(?:data\s+)?class\s+(\w+)""")

        return pattern.find(code)?.groupValues?.get(1)
            ?: code.lines()
                .firstOrNull { line ->
                    line.contains("class ") && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("/*")
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
        return com.example.archassistant.model.ArchitecturalRule(
            id = "temp_${java.util.UUID.randomUUID()}",
            name = "Converted rule from ${input.fromPackage}",
            description = "Rule converted from API request",
            type = convertType(input.type),
            fromPackage = normalizePackagePattern(input.fromPackage),
            toPackage = input.toPackage?.let { normalizePackagePattern(it) },
            toPackages = input.toPackages?.map { normalizePackagePattern(it) },
            constraint = convertConstraint(input.type),
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

    private fun convertConstraint(typeString: String): com.example.archassistant.model.ConstraintType {
        return when (typeString.lowercase()) {
            "nodependency", "no_dependency" -> com.example.archassistant.model.ConstraintType.NO_DEPENDENCY
            "mustdepend", "must_depend" -> com.example.archassistant.model.ConstraintType.MUST_DEPEND
            "namingsuffix", "naming_suffix" -> com.example.archassistant.model.ConstraintType.NAMING_SUFFIX
            "namingprefix", "naming_prefix" -> com.example.archassistant.model.ConstraintType.NAMING_PREFIX
            "hasannotation", "has_annotation" -> com.example.archassistant.model.ConstraintType.HAS_ANNOTATION
            "noannotation", "no_annotation" -> com.example.archassistant.model.ConstraintType.NO_ANNOTATION
            else -> com.example.archassistant.model.ConstraintType.NO_DEPENDENCY
        }
    }

    /**
     * Нормализация паттерна пакета для ArchUnit
     * ..service.. → ..service..*
     * *Service → *Service
     */
    private fun normalizePackagePattern(pattern: String): String {
        return PackagePatternBuilder.buildWildcardPatterns(listOf(pattern)).firstOrNull() ?: pattern
    }
}