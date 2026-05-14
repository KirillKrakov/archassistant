package com.example.archassistant.service.rules.repository

import com.example.archassistant.model.core.Severity
import com.example.archassistant.model.core.Violation
import com.example.archassistant.model.generation.ValidationResult
import com.example.archassistant.model.rules.*
import com.example.archassistant.util.pack.PackagePatternBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime

@Service
class YamlRuleRepository(
    @Qualifier("yamlObjectMapper")
    private val objectMapper: ObjectMapper,
    @Value("\${archassistant.config-root:.archassistant}")
    private val configRootPath: String
) {

    private val logger = LoggerFactory.getLogger(YamlRuleRepository::class.java)

    fun load(projectId: String): RulesConfig? {
        val configFile = getConfigFile(projectId)

        return if (configFile.exists() && configFile.isFile) {
            try {
                objectMapper.readValue(configFile, RulesConfig::class.java)
            } catch (e: Exception) {
                logger.error("Failed to load rules config for $projectId: ${e.message}", e)
                null
            }
        } else {
            logger.debug("Config file not found for $projectId: ${configFile.absolutePath}")
            null
        }
    }

    fun save(config: RulesConfig): Boolean {
        return try {
            val validation = validate(config)
            if (!validation.passed) {
                logger.warn(
                    "Refusing to save invalid rules config for {}: {}",
                    config.projectId,
                    validation.violations.joinToString("; ") { it.description }
                )
                return false
            }

            val configFile = getConfigFile(config.projectId)
            configFile.parentFile?.mkdirs()

            val persisted = config.touch()

            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(configFile, persisted)

            logger.info("Saved rules config for ${config.projectId}: ${configFile.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("Failed to save rules config: ${e.message}", e)
            false
        }
    }

    fun createDefault(projectId: String, projectType: String = "SPRING_BOOT"): RulesConfig {
        val resolvedType = runCatching { ProjectType.valueOf(projectType.uppercase()) }
            .getOrDefault(ProjectType.SPRING_BOOT)

        val now = LocalDateTime.now().toString()

        return RulesConfig(
            projectId = projectId,
            projectType = resolvedType,
            rules = emptyList(),
            settings = RuleSettings(),
            createdAt = now,
            updatedAt = now
        )
    }

    fun validate(config: RulesConfig): ValidationResult {
        val violations = mutableListOf<Violation>()
        val ids = mutableSetOf<String>()

        if (config.projectId.isBlank()) {
            violations.add(
                Violation(
                    ruleId = "config_validation",
                    description = "projectId cannot be empty",
                    className = "RulesConfig",
                    severity = Severity.CRITICAL
                )
            )
        }

        config.rules.forEachIndexed { index, rule ->
            val ruleNo = index + 1
            val ruleId = rule.id.ifBlank { "rule_$ruleNo" }

            if (rule.id.isBlank()) {
                violations.add(
                    Violation(
                        ruleId = ruleId,
                        description = "Rule ID cannot be empty",
                        className = "ArchitecturalRule",
                        severity = Severity.ERROR
                    )
                )
            } else if (!ids.add(rule.id)) {
                violations.add(
                    Violation(
                        ruleId = rule.id,
                        description = "Duplicate rule ID: ${rule.id}",
                        className = "ArchitecturalRule",
                        severity = Severity.ERROR
                    )
                )
            }

            validateRuleShape(rule, ruleNo, violations)
            validateSelector(
                rule = rule,
                side = "from",
                mode = rule.fromSelectorMode,
                packageValue = rule.fromPackage,
                classTypeValue = rule.fromClassType,
                layerTypeValue = rule.fromLayerType,
                violations = violations,
                ruleNo = ruleNo
            )

            validateSelector(
                rule = rule,
                side = "to",
                mode = rule.toSelectorMode,
                packageValue = rule.toPackage ?: rule.toPackages?.firstOrNull() ?: "",
                classTypeValue = rule.toClassType,
                layerTypeValue = rule.toLayerType,
                violations = violations,
                ruleNo = ruleNo
            )
        }

        return if (violations.isEmpty()) {
            ValidationResult.success("Config is valid")
        } else {
            ValidationResult.failure(violations)
        }
    }

    private fun validateRuleShape(
        rule: ArchitecturalRule,
        ruleNo: Int,
        violations: MutableList<Violation>
    ) {
        val ruleId = rule.id.ifBlank { "rule_$ruleNo" }

        val allowed = RuleValidationCatalog.allowedConstraints(rule.type)
        if (rule.constraint !in allowed) {
            violations.add(
                Violation(
                    ruleId = ruleId,
                    description = "Invalid constraint '${rule.constraint}' for rule type '${rule.type}'",
                    className = "ArchitecturalRule",
                    severity = Severity.ERROR
                )
            )
        }

        RuleValidationCatalog.expectedRequiredFields(rule).forEach { field ->
            val missing = when (field) {
                "from_package" -> rule.fromPackage.isBlank()
                "to_package|to_packages" -> rule.toPackage.isNullOrBlank() && rule.toPackages.isNullOrEmpty()
                "pattern" -> rule.pattern.isNullOrBlank()
                "annotation" -> rule.annotation.isNullOrBlank()
                "slice_pattern" -> rule.slicePattern.isNullOrBlank()
                else -> false
            }

            if (missing) {
                violations.add(
                    Violation(
                        ruleId = ruleId,
                        description = "Missing required field(s) for ${rule.type}/${rule.constraint}: $field",
                        className = "ArchitecturalRule",
                        severity = Severity.ERROR
                    )
                )
            }
        }

        when (rule.type) {
            RuleType.DEPENDENCY, RuleType.LAYER_ISOLATION -> {
                if (rule.constraint !in setOf(ConstraintType.NO_DEPENDENCY, ConstraintType.MUST_DEPEND)) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "Invalid constraint for dependency rule: ${rule.constraint}",
                            className = "ArchitecturalRule",
                            severity = Severity.WARNING
                        )
                    )
                }
            }

            RuleType.NAMING_CONVENTION -> {
                if (rule.constraint !in setOf(ConstraintType.NAMING_SUFFIX, ConstraintType.NAMING_PREFIX)) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "Invalid constraint for naming rule: ${rule.constraint}",
                            className = "ArchitecturalRule",
                            severity = Severity.WARNING
                        )
                    )
                }
                if (rule.pattern.isNullOrBlank()) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "Naming rule pattern cannot be empty",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            RuleType.ANNOTATION_CHECK -> {
                if (rule.constraint !in setOf(ConstraintType.HAS_ANNOTATION, ConstraintType.NO_ANNOTATION)) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "Invalid constraint for annotation rule: ${rule.constraint}",
                            className = "ArchitecturalRule",
                            severity = Severity.WARNING
                        )
                    )
                }
                if (rule.annotation.isNullOrBlank()) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "Annotation rule annotation cannot be empty",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            RuleType.CYCLE_CHECK -> {
                if (rule.slicePattern.isNullOrBlank()) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "Cycle rule slice_pattern cannot be empty",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
                if (rule.maxCycleLength != null && rule.maxCycleLength <= 0) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "max_cycle_length must be greater than 0",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            RuleType.INHERITANCE_CHECK, RuleType.INTERFACE_CHECK -> {
                if (rule.toPackage.isNullOrBlank() && rule.toPackages.isNullOrEmpty()) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "Target package cannot be empty for ${rule.type}",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            RuleType.MODIFIER_CHECK,
            RuleType.METHOD_SIGNATURE_CHECK,
            RuleType.FIELD_CHECK,
            RuleType.EXCEPTION_CHECK -> {
                if (rule.fromPackage.isBlank()) {
                    violations.add(
                        Violation(
                            ruleId = ruleId,
                            description = "from_package cannot be empty for ${rule.type}",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            RuleType.CUSTOM -> Unit
        }
    }

    private fun validateSelector(
        rule: ArchitecturalRule,
        side: String,
        mode: SelectorMode,
        packageValue: String,
        classTypeValue: Any?,
        layerTypeValue: Any?,
        violations: MutableList<Violation>,
        ruleNo: Int
    ) {
        if (side == "to" && rule.type in setOf(RuleType.NAMING_CONVENTION, RuleType.ANNOTATION_CHECK)) {
            return
        }

        if (mode !in RuleValidationCatalog.selectorModesAllowed(rule.type)) {
            violations.add(
                Violation(
                    ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                    description = "Selector mode $mode is not allowed for ${rule.type}",
                    className = "ArchitecturalRule",
                    severity = Severity.ERROR
                )
            )
        }

        when (mode) {
            SelectorMode.PACKAGE -> {
                if (packageValue.isBlank() && side == "from" && rule.type != RuleType.CYCLE_CHECK) {
                    violations.add(
                        Violation(
                            ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                            description = "$side package cannot be empty for PACKAGE selector",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                } else if (packageValue.isNotBlank() && !isValidWildcardPattern(packageValue)) {
                    violations.add(
                        Violation(
                            ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                            description = "Invalid wildcard pattern in $side package: $packageValue",
                            className = "ArchitecturalRule",
                            severity = Severity.WARNING
                        )
                    )
                }
            }

            SelectorMode.CLASS_TYPE -> {
                if (classTypeValue == null) {
                    violations.add(
                        Violation(
                            ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                            description = "$side class type cannot be empty for CLASS_TYPE selector",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            SelectorMode.LAYER -> {
                if (layerTypeValue == null) {
                    violations.add(
                        Violation(
                            ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                            description = "$side layer type cannot be empty for LAYER selector",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            SelectorMode.ANNOTATION -> {
                if (rule.annotation.isNullOrBlank()) {
                    violations.add(
                        Violation(
                            ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                            description = "Annotation cannot be empty for ANNOTATION selector",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }

            SelectorMode.MEMBER -> {
                if (
                    rule.fromMethodNamePattern.isNullOrBlank() &&
                    rule.toMethodNamePattern.isNullOrBlank() &&
                    rule.fromFieldNamePattern.isNullOrBlank() &&
                    rule.toFieldNamePattern.isNullOrBlank() &&
                    rule.fromReturnType.isNullOrBlank() &&
                    rule.toReturnType.isNullOrBlank() &&
                    rule.fromFieldType.isNullOrBlank() &&
                    rule.toFieldType.isNullOrBlank()
                ) {
                    violations.add(
                        Violation(
                            ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                            description = "Member selector requires method/field signature information",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                }
            }
        }
    }

    private fun isValidWildcardPattern(pattern: String): Boolean {
        return try {
            PackagePatternBuilder.buildRegex(pattern)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getConfigFile(projectId: String): File {
        val safeProjectId = projectId.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return Paths.get(configRootPath, safeProjectId, "rules.yml").toFile()
    }

    fun exists(projectId: String): Boolean = getConfigFile(projectId).exists()

    fun delete(projectId: String): Boolean {
        return try {
            getConfigFile(projectId).delete()
        } catch (e: Exception) {
            logger.warn("Failed to delete config for $projectId: ${e.message}")
            false
        }
    }
}