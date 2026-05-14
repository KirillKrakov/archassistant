package com.example.archassistant.controller

import com.example.archassistant.dto.GeneratedFileSyncRequest
import com.example.archassistant.dto.GeneratedFileSyncResponse
import com.example.archassistant.model.RulesConfig
import com.example.archassistant.service.context.ProjectContextService
import com.example.archassistant.service.context.overlay.GeneratedSourceOverlayService
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import com.example.archassistant.service.sync.ProjectOperationLockService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/generated-files")
@CrossOrigin(origins = ["*"])
class GeneratedFilesController(
    private val overlayService: GeneratedSourceOverlayService,
    private val projectContextService: ProjectContextService,
    private val ruleRepository: YamlRuleRepository,
    private val operationLockService: ProjectOperationLockService
) {

    private val logger = LoggerFactory.getLogger(GeneratedFilesController::class.java)

    @PostMapping("/{projectId}/sync")
    fun syncGeneratedFiles(
        @PathVariable projectId: String,
        @RequestBody request: GeneratedFileSyncRequest
    ): ResponseEntity<GeneratedFileSyncResponse> {
        return operationLockService.withLock(projectId) {
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
                return@withLock ResponseEntity.badRequest().body(result)
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

            ResponseEntity.ok(
                result.copy(
                    contextRefreshed = refreshed,
                    warnings = warnings
                )
            )
        }
    }

    @DeleteMapping("/{projectId}")
    fun clearOverlay(@PathVariable projectId: String): ResponseEntity<Map<String, Any>> {
        return operationLockService.withLock(projectId) {
            logger.info("Clearing generated-files overlay for projectId={}", projectId)

            val cleared = overlayService.clearOverlay(projectId)
            if (!cleared) {
                return@withLock ResponseEntity.internalServerError()
                    .body(mapOf("success" to false, "error" to "Failed to clear overlay"))
            }

            projectContextService.invalidate(projectId)
            val refreshed = runCatching {
                projectContextService.getProjectContext(projectId, refresh = true)
            }.getOrNull() != null

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "projectId" to projectId,
                    "contextRefreshed" to refreshed
                )
            )
        }
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