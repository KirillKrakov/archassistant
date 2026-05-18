package com.example.archassistant.model.rules

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