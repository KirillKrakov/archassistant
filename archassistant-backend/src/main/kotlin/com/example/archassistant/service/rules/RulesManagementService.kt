package com.example.archassistant.service.rules

import com.example.archassistant.dto.rules.*
import com.example.archassistant.model.rules.RulesConfig
import com.example.archassistant.service.context.WorkspaceModuleSuggestions
import com.example.archassistant.service.context.WorkspaceProjectScanner
import com.example.archassistant.service.rules.mapper.RulesConfigMapper
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RulesManagementService(
    private val ruleRepository: YamlRuleRepository,
    private val projectScanner: WorkspaceProjectScanner,
    private val rulesConfigMapper: RulesConfigMapper
) {
    private val logger = LoggerFactory.getLogger(RulesManagementService::class.java)

    fun getRules(projectId: String): RulesConfigDto {
        val config = ruleRepository.load(projectId)
            ?: RulesConfig(projectId = projectId, rules = emptyList())

        return rulesConfigMapper.toDto(config)
    }

    fun saveRules(projectId: String, dto: RulesConfigDto): RulesSaveResponse {
        logger.info("Saving rules config for project: {}", projectId)

        if (dto.projectId != projectId) {
            return RulesSaveResponse(
                success = false,
                error = "projectId mismatch"
            )
        }

        val existingConfig = ruleRepository.load(projectId)
        val incoming = rulesConfigMapper.toModel(dto)

        val mergedConfig = if (existingConfig != null) {
            incoming.copy(
                projectPath = incoming.projectPath ?: existingConfig.projectPath,
                projectType = incoming.projectType ?: existingConfig.projectType,
                settings = incoming.settings ?: existingConfig.settings,
                version = incoming.version ?: existingConfig.version
            )
        } else {
            incoming
        }

        val validationResult = ruleRepository.validate(mergedConfig)
        if (!validationResult.passed) {
            return RulesSaveResponse(
                success = false,
                error = "Invalid configuration",
                violations = validationResult.violations.map { it.description }
            )
        }

        val success = ruleRepository.save(mergedConfig)
        return if (success) {
            RulesSaveResponse(success = true, projectId = projectId)
        } else {
            RulesSaveResponse(success = false, error = "Failed to save configuration")
        }
    }

    fun suggestRules(projectId: String, projectPath: String?): List<WorkspaceModuleSuggestions>? {
        logger.info("Generating workspace rule suggestions for project: {}, path: {}", projectId, projectPath)

        return if (!projectPath.isNullOrBlank()) {
            projectScanner.scanWorkspace(projectPath, projectId)
        } else {
            projectScanner.scanProjectFromConfig(projectId)
        }
    }

    fun saveProjectPath(projectId: String, projectPath: String): ProjectPathResponse {
        val config = ruleRepository.load(projectId)
            ?: RulesConfig(projectId = projectId, rules = emptyList())

        val updatedConfig = config.copy(projectPath = projectPath)
        val success = ruleRepository.save(updatedConfig)

        return if (success) {
            ProjectPathResponse(
                success = true,
                projectId = projectId,
                projectPath = projectPath
            )
        } else {
            ProjectPathResponse(
                success = false,
                error = "Failed to save project path"
            )
        }
    }

    fun deleteRules(projectId: String): RulesDeleteResponse {
        logger.info("Deleting rules config for project: {}", projectId)

        val success = ruleRepository.delete(projectId)
        return if (success) {
            RulesDeleteResponse(success = true, projectId = projectId)
        } else {
            RulesDeleteResponse(success = false, error = "Failed to delete configuration")
        }
    }
}