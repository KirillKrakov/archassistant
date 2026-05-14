package com.example.archassistant.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Конфигурация архитектурных правил проекта
 * Сериализуется/десериализуется в YAML
 */
data class RulesConfig(
    @JsonProperty("version")
    val version: String? = "1.0",

    @JsonProperty("project_id")
    val projectId: String,

    @JsonProperty("project_type")
    val projectType: ProjectType? = ProjectType.SPRING_BOOT,

    @JsonProperty("rules")
    val rules: List<ArchitecturalRule> = emptyList(),

    @JsonProperty("settings")
    val settings: RuleSettings? = RuleSettings(),

    @JsonProperty("project_path")
    val projectPath: String? = null,

    @JsonProperty("created_at")
    val createdAt: String? = LocalDateTime.now().toString(),

    @JsonProperty("updated_at")
    val updatedAt: String? = LocalDateTime.now().toString()
) {
    /**
     * Получение только включённых правил
     */
    fun getEnabledRules(): List<ArchitecturalRule> = rules.filter { it.enabled }

    /**
     * Получение правил по типу
     */
    fun getRulesByType(type: RuleType): List<ArchitecturalRule> =
        rules.filter { it.enabled && it.type == type }

    /**
     * Обновление правила по ID
     */
    fun updateRule(ruleId: String, updater: (ArchitecturalRule) -> ArchitecturalRule): RulesConfig {
        val updatedRules = rules.map { rule ->
            if (rule.id == ruleId) updater(rule) else rule
        }
        return copy(rules = updatedRules, updatedAt = LocalDateTime.now().toString())
    }

    fun touch(now: String = LocalDateTime.now().toString()): RulesConfig {
        return copy(
            createdAt = createdAt ?: now,
            updatedAt = now
        )
    }

    fun withProjectPath(
        projectPath: String?,
        now: String = LocalDateTime.now().toString()
    ): RulesConfig {
        return copy(
            projectPath = projectPath,
            createdAt = createdAt ?: now,
            updatedAt = now
        )
    }
}

/**
 * Тип проекта для предзаполнения шаблонов правил
 */
enum class ProjectType {
    SPRING_BOOT,
    ANDROID_MVVM,
    KTOR,
    MICRONAUT,
    QUARKUS,
    CUSTOM
}

/**
 * Настройки обработки правил
 */
data class RuleSettings(
    @JsonProperty("max_iterations")
    val maxIterations: Int = 3,

    @JsonProperty("timeout_seconds")
    val timeoutSeconds: Int = 30,

    @JsonProperty("default_strategy")
    val defaultStrategy: String = "HYBRID",

    @JsonProperty("fail_on_critical")
    val failOnCritical: Boolean = true,

    @JsonProperty("auto_fix_naming")
    val autoFixNaming: Boolean = false
)