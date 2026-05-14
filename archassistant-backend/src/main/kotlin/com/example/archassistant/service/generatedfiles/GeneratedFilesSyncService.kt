package com.example.archassistant.service.generatedfiles

import com.example.archassistant.dto.generatedfiles.request.GeneratedFileSyncRequest
import com.example.archassistant.dto.generatedfiles.response.GeneratedFileSyncResponse
import com.example.archassistant.dto.generatedfiles.response.GeneratedFilesClearResponse
import com.example.archassistant.model.RulesConfig
import com.example.archassistant.service.context.ProjectContextService
import com.example.archassistant.service.context.overlay.GeneratedSourceOverlayService
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GeneratedFilesSyncService(
    private val overlayService: GeneratedSourceOverlayService,
    private val projectContextService: ProjectContextService,
    private val ruleRepository: YamlRuleRepository
) {
    private val logger = LoggerFactory.getLogger(GeneratedFilesSyncService::class.java)

    fun syncGeneratedFiles(
        projectId: String,
        request: GeneratedFileSyncRequest
    ): GeneratedFileSyncResponse {
        logger.info(
            "Sync generated files for projectId={}, files={}, projectPathProvided={}",
            projectId,
            request.files.size,
            !request.projectPath.isNullOrBlank()
        )

        val result = overlayService.syncGeneratedFiles(
            projectId = projectId,
            projectPathOverride = request.projectPath,
            files = request.files
        )

        if (!result.success) {
            return result
        }

        if (!request.projectPath.isNullOrBlank()) {
            persistProjectPathBestEffort(projectId, request.projectPath)
        }

        val refreshed = runCatching {
            projectContextService.getProjectContext(
                projectId = projectId,
                refresh = true,
                projectPathOverride = request.projectPath
            )
        }.getOrNull() != null

        val warnings = buildList {
            addAll(result.warnings)
            if (!refreshed) {
                add("Overlay synced, but project context refresh failed. Check the backend logs and base project build.")
            }
        }

        return result.copy(
            contextRefreshed = refreshed,
            warnings = warnings
        )
    }

    fun clearOverlay(projectId: String): GeneratedFilesClearResponse {
        logger.info("Clearing generated-files overlay for projectId={}", projectId)

        val cleared = overlayService.clearOverlay(projectId)
        if (!cleared) {
            return GeneratedFilesClearResponse(
                success = false,
                error = "Failed to clear overlay"
            )
        }

        projectContextService.invalidate(projectId)

        val refreshed = runCatching {
            projectContextService.getProjectContext(projectId, refresh = true)
        }.getOrNull() != null

        return GeneratedFilesClearResponse(
            success = true,
            projectId = projectId,
            contextRefreshed = refreshed
        )
    }

    private fun persistProjectPathBestEffort(
        projectId: String,
        projectPath: String
    ) {
        try {
            val existing = ruleRepository.load(projectId)
                ?: RulesConfig(projectId = projectId, rules = emptyList())

            if (existing.projectPath != projectPath) {
                val saved = ruleRepository.save(existing.copy(projectPath = projectPath))
                if (!saved) {
                    logger.warn("Overlay synced, but projectPath could not be persisted for projectId={}", projectId)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to persist projectPath for projectId={}: {}", projectId, e.message)
        }
    }
}