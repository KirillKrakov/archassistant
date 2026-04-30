package com.example.archassistant.service

import com.example.archassistant.config.YamlConfig
import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.RuleSettings
import com.example.archassistant.model.RulesConfig
import com.example.archassistant.model.SelectorMode
import com.example.archassistant.model.ValidationResult
import com.example.archassistant.model.Violation
import com.example.archassistant.model.Severity
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Paths

/**
 * Репозиторий для работы с YAML-конфигурацией правил
 * Хранит правила в файловой системе: .archassistant/rules.yml
 */
@Service
class YamlRuleRepository(
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
        return RulesConfig(
            projectId = projectId,
            projectType = com.example.archassistant.model.ProjectType.valueOf(projectType),
            rules = emptyList(),
            settings = RuleSettings()
        )
    }

    /**
     * Валидация структуры конфигурации
     */
    fun validate(config: RulesConfig): ValidationResult {
        val violations = mutableListOf<Violation>()

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
            if (rule.id.isBlank()) {
                violations.add(
                    Violation(
                        ruleId = "rule_${index + 1}",
                        description = "Rule ID cannot be empty",
                        className = "ArchitecturalRule",
                        severity = Severity.ERROR
                    )
                )
            }

            if (rule.fromSelectorMode == SelectorMode.PACKAGE && !isValidWildcardPattern(rule.fromPackage)) {
                violations.add(
                    Violation(
                        ruleId = "rule_${index + 1}",
                        description = "Invalid wildcard pattern in fromPackage: ${rule.fromPackage}",
                        className = "ArchitecturalRule",
                        severity = Severity.WARNING
                    )
                )
            }

            if (rule.toSelectorMode == SelectorMode.PACKAGE) {
                val targets = when {
                    !rule.toPackages.isNullOrEmpty() -> rule.toPackages
                    !rule.toPackage.isNullOrBlank() -> listOf(rule.toPackage)
                    else -> emptyList()
                }

                targets.forEach { target ->
                    if (!isValidWildcardPattern(target)) {
                        violations.add(
                            Violation(
                                ruleId = "rule_${index + 1}",
                                description = "Invalid wildcard pattern in target package: $target",
                                className = "ArchitecturalRule",
                                severity = Severity.WARNING
                            )
                        )
                    }
                }
            }
        }

        return if (violations.isEmpty()) {
            ValidationResult.success("Config is valid")
        } else {
            ValidationResult.failure(violations)
        }
    }

    /**
     * Проверка валидности wildcard-паттерна
     */
    private fun isValidWildcardPattern(pattern: String): Boolean {
        return try {
            pattern.toRegexPatternForValidation().toRegex()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Упрощённая версия toRegexPattern для валидации
     */
    private fun String.toRegexPatternForValidation(): String {
        val subpackageWildcard = "__SUBPKG__"
        return this
            .replace("**", ".*")
            .replace("..*", subpackageWildcard)
            .replace("*", "[^.]*")
            .replace(".", "\\.")
            .replace(subpackageWildcard, "(\\..*)?")
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