package com.example.archassistant.service.context.classpath

import com.example.archassistant.service.rules.repository.YamlRuleRepository
import org.springframework.stereotype.Service

@Service
class ProjectPathResolver(
    private val ruleRepository: YamlRuleRepository
) {

    fun resolveProjectPath(
        projectId: String,
        projectPathOverride: String? = null
    ): String {
        projectPathOverride
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val fromConfig = ruleRepository.load(projectId)
            ?.projectPath
            ?.trim()
            .orEmpty()

        if (fromConfig.isNotBlank()) {
            return fromConfig
        }

        val envCandidates = listOf(
            "ARCHASSISTANT_PROJECT_PATH_${projectId.uppercase()}",
            "ARCHASSISTANT_PROJECT_PATH",
            "PROJECT_PATH"
        )

        return envCandidates
            .asSequence()
            .mapNotNull { key -> System.getenv(key)?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }
}