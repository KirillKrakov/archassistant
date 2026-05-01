package com.example.archassistant.service

import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.ConstraintType
import com.example.archassistant.model.RuleSettings
import com.example.archassistant.model.RuleType
import com.example.archassistant.model.RulesConfig
import com.example.archassistant.model.SelectorMode
import com.example.archassistant.model.ValidationResult
import com.example.archassistant.model.Violation
import com.example.archassistant.model.Severity
import com.example.archassistant.model.ProjectType
import com.example.archassistant.util.PackagePatternBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Paths

/**
 * Репозиторий для работы с YAML-конфигурацией правил
 * Хранит правила в файловой системе: .archassistant/rules.yml
 */
@Service
class YamlRuleRepository(
    @Qualifier("yamlObjectMapper")
    private val objectMapper: ObjectMapper,
    private val configRootPath: String = ".archassistant"
) {

    private val logger = LoggerFactory.getLogger(YamlRuleRepository::class.java)

    /**
     * Загрузка конфигурации правил для проекта
     */
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

    /**
     * Сохранение конфигурации правил
     */
    fun save(config: RulesConfig): Boolean {
        return try {
            val configFile = getConfigFile(config.projectId)
            configFile.parentFile?.mkdirs()

            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(configFile, config)

            logger.info("Saved rules config for ${config.projectId}: ${configFile.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("Failed to save rules config: ${e.message}", e)
            false
        }
    }

    /**
     * Создание конфигурации по умолчанию для проекта
     */
    fun createDefault(projectId: String, projectType: String = "SPRING_BOOT"): RulesConfig {
        val resolvedType = runCatching { ProjectType.valueOf(projectType) }
            .getOrDefault(ProjectType.SPRING_BOOT)

        return RulesConfig(
            projectId = projectId,
            projectType = resolvedType,
            rules = emptyList(),
            settings = RuleSettings()
        )
    }

    /**
     * Валидация структуры конфигурации
     */
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

            if (rule.id.isBlank()) {
                violations.add(
                    Violation(
                        ruleId = "rule_$ruleNo",
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

            when (rule.type) {
                RuleType.DEPENDENCY, RuleType.LAYER_ISOLATION -> {
                    if (rule.constraint !in setOf(ConstraintType.NO_DEPENDENCY, ConstraintType.MUST_DEPEND)) {
                        violations.add(
                            Violation(
                                ruleId = rule.id.ifBlank { "rule_$ruleNo" },
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
                                ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                                description = "Invalid constraint for naming rule: ${rule.constraint}",
                                className = "ArchitecturalRule",
                                severity = Severity.WARNING
                            )
                        )
                    }
                    if (rule.pattern.isNullOrBlank()) {
                        violations.add(
                            Violation(
                                ruleId = rule.id.ifBlank { "rule_$ruleNo" },
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
                                ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                                description = "Invalid constraint for annotation rule: ${rule.constraint}",
                                className = "ArchitecturalRule",
                                severity = Severity.WARNING
                            )
                        )
                    }
                    if (rule.annotation.isNullOrBlank()) {
                        violations.add(
                            Violation(
                                ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                                description = "Annotation rule annotation cannot be empty",
                                className = "ArchitecturalRule",
                                severity = Severity.ERROR
                            )
                        )
                    }
                }

                RuleType.CUSTOM -> Unit
            }
        }

        return if (violations.isEmpty()) {
            ValidationResult.success("Config is valid")
        } else {
            ValidationResult.failure(violations)
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

        when (mode) {
            SelectorMode.PACKAGE -> {
                if (packageValue.isBlank()) {
                    violations.add(
                        Violation(
                            ruleId = rule.id.ifBlank { "rule_$ruleNo" },
                            description = "$side package cannot be empty for PACKAGE selector",
                            className = "ArchitecturalRule",
                            severity = Severity.ERROR
                        )
                    )
                } else if (!isValidWildcardPattern(packageValue)) {
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
        }
    }

    /**
     * Проверка валидности wildcard-паттерна
     */
    private fun isValidWildcardPattern(pattern: String): Boolean {
        return try {
            PackagePatternBuilder.buildRegex(pattern)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Получение пути к конфигурационному файлу
     */
    private fun getConfigFile(projectId: String): File {
        val safeProjectId = projectId.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return Paths.get(configRootPath, safeProjectId, "rules.yml").toFile()
    }

    /**
     * Проверка существования конфига
     */
    fun exists(projectId: String): Boolean {
        return getConfigFile(projectId).exists()
    }

    /**
     * Удаление конфигурации
     */
    fun delete(projectId: String): Boolean {
        return try {
            getConfigFile(projectId).delete()
        } catch (e: Exception) {
            logger.warn("Failed to delete config for $projectId: ${e.message}")
            false
        }
    }
}