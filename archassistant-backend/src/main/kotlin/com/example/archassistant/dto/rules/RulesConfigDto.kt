package com.example.archassistant.dto.rules

import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.model.rules.ProjectType
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
)