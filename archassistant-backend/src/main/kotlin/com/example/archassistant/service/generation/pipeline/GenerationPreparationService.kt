package com.example.archassistant.service.generation.pipeline

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.model.generation.PreparedGenerationRequest
import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.service.context.ProjectContextService
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import org.springframework.stereotype.Service

@Service
class GenerationPreparationService(
    private val projectContextService: ProjectContextService,
    private val ruleRepository: YamlRuleRepository
) {

    fun prepare(request: CodeGenerationRequest): PreparedGenerationRequest {
        if (request.prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }

        val projectContext = projectContextService.requireProjectContext(
            projectId = request.projectId,
            refresh = false,
            projectPathOverride = null
        )

        return PreparedGenerationRequest(
            projectContext = projectContext,
            rules = resolveRules(request),
            normalizedTargetPackage = request.normalizedTargetPackage(),
            normalizedExpectedClassName = request.normalizedExpectedClassName(),
            normalizedExistingTypes = request.normalizedExistingTypes()
        )
    }

    private fun resolveRules(request: CodeGenerationRequest): List<ArchitecturalRule> {
        val configRules = ruleRepository.load(request.projectId)?.getEnabledRules().orEmpty()

        val selected = request.rules
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.let { ids -> configRules.filter { it.id in ids } }

        return selected ?: configRules
    }

    private fun CodeGenerationRequest.normalizedTargetPackage(): String? =
        context?.targetPackage
            ?.trim()
            ?.trim('.')
            ?.takeIf { it.isNotBlank() }

    private fun CodeGenerationRequest.normalizedExpectedClassName(): String? =
        expectedClassName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace('$', '.')

    private fun CodeGenerationRequest.normalizedExistingTypes(): List<String> =
        context?.existingTypes.orEmpty()
            .map { it.trim().replace('$', '.') }
            .filter { it.isNotBlank() }
            .distinct()
}