package com.example.archassistant.service.rules.mapper

import com.example.archassistant.dto.rules.RuleSettingsDto
import com.example.archassistant.dto.rules.RulesConfigDto
import com.example.archassistant.model.rules.ProjectType
import com.example.archassistant.model.rules.RuleSettings
import com.example.archassistant.model.rules.RulesConfig
import org.springframework.stereotype.Component

@Component
object RulesConfigMapper {

    fun toModel(dto: RulesConfigDto): RulesConfig {
        return RulesConfig(
            version = dto.version,
            projectId = dto.projectId,
            projectType = dto.projectType ?: ProjectType.SPRING_BOOT,
            rules = dto.rules,
            settings = dto.settings?.let { toModel(it) } ?: RuleSettings(),
            projectPath = dto.projectPath,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }

    fun toDto(model: RulesConfig): RulesConfigDto {
        return RulesConfigDto(
            version = model.version,
            projectId = model.projectId,
            projectType = model.projectType,
            rules = model.rules,
            settings = model.settings?.let { toDto(it) },
            projectPath = model.projectPath,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt
        )
    }

    fun toModel(dto: RuleSettingsDto): RuleSettings {
        return RuleSettings(
            maxIterations = dto.maxIterations,
            timeoutSeconds = dto.timeoutSeconds,
            defaultStrategy = dto.defaultStrategy,
            failOnCritical = dto.failOnCritical,
            autoFixNaming = dto.autoFixNaming
        )
    }

    fun toDto(model: RuleSettings): RuleSettingsDto {
        return RuleSettingsDto(
            maxIterations = model.maxIterations,
            timeoutSeconds = model.timeoutSeconds,
            defaultStrategy = model.defaultStrategy,
            failOnCritical = model.failOnCritical,
            autoFixNaming = model.autoFixNaming
        )
    }

    fun merge(existing: RulesConfig?, incoming: RulesConfig): RulesConfig {
        if (existing == null) return incoming

        return incoming.copy(
            projectPath = incoming.projectPath ?: existing.projectPath,
            projectType = incoming.projectType ?: existing.projectType,
            settings = incoming.settings ?: existing.settings,
            version = incoming.version ?: existing.version
        )
    }
}