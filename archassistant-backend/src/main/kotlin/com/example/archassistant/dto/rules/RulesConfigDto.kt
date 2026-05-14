package com.example.archassistant.dto.rules

import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.ProjectType
import com.example.archassistant.model.RulesConfig
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RulesConfigDto(
    @JsonProperty("version")
    val version: String? = "1.0",

    @JsonProperty("project_id")
    val projectId: String,

    @JsonProperty("project_type")
    val projectType: ProjectType? = ProjectType.SPRING_BOOT,

    @JsonProperty("rules")
    val rules: List<ArchitecturalRule> = emptyList(),

    @JsonProperty("settings")
    val settings: RuleSettingsDto? = RuleSettingsDto(),

    @JsonProperty("project_path")
    val projectPath: String? = null,

    @JsonProperty("created_at")
    val createdAt: String? = LocalDateTime.now().toString(),

    @JsonProperty("updated_at")
    val updatedAt: String? = LocalDateTime.now().toString()
) {
    fun toModel(): RulesConfig {
        return RulesConfig(
            version = version,
            projectId = projectId,
            projectType = projectType,
            rules = rules,
            settings = settings?.toModel(),
            projectPath = projectPath,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromModel(model: RulesConfig): RulesConfigDto {
            return RulesConfigDto(
                version = model.version,
                projectId = model.projectId,
                projectType = model.projectType,
                rules = model.rules,
                settings = model.settings?.let { RuleSettingsDto.fromModel(it) },
                projectPath = model.projectPath,
                createdAt = model.createdAt,
                updatedAt = model.updatedAt
            )
        }
    }
}