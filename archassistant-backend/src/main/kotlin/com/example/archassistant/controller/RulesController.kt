package com.example.archassistant.controller

import com.example.archassistant.model.RulesConfig
import com.example.archassistant.service.context.WorkspaceModuleSuggestions
import com.example.archassistant.service.context.WorkspaceProjectScanner
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = ["*"])
class RulesController(
    private val ruleRepository: YamlRuleRepository,
    private val projectScanner: WorkspaceProjectScanner
) {

    private val logger = LoggerFactory.getLogger(RulesController::class.java)

    @GetMapping("/{projectId}")
    fun getRules(@PathVariable projectId: String): ResponseEntity<Any> {
        val config = ruleRepository.load(projectId)
        return ResponseEntity.ok(
            config ?: RulesConfig(
                projectId = projectId,
                rules = emptyList()
            )
        )
    }

    @PostMapping("/{projectId}")
    fun saveRules(
        @PathVariable projectId: String,
        @RequestBody config: RulesConfig
    ): ResponseEntity<Map<String, Any>> {
        logger.info("Saving rules config for project: {}", projectId)

        if (config.projectId != projectId) {
            return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "error" to "projectId mismatch"))
        }

        val existingConfig = ruleRepository.load(projectId)

        val mergedConfig = if (existingConfig != null) {
            config.copy(
                projectPath = config.projectPath ?: existingConfig.projectPath,
                projectType = config.projectType ?: existingConfig.projectType,
                settings = config.settings ?: existingConfig.settings,
                version = config.version ?: existingConfig.version
            )
        } else {
            config
        }

        val validationResult = ruleRepository.validate(mergedConfig)
        if (!validationResult.passed) {
            return ResponseEntity.badRequest()
                .body(
                    mapOf(
                        "success" to false,
                        "error" to "Invalid configuration",
                        "violations" to validationResult.violations
                    )
                )
        }

        val success = ruleRepository.save(mergedConfig)

        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "projectId" to projectId))
        } else {
            ResponseEntity.internalServerError()
                .body(mapOf("success" to false, "error" to "Failed to save configuration"))
        }
    }

    @GetMapping("/{projectId}/suggest")
    fun suggestRules(
        @PathVariable projectId: String,
        @RequestParam projectPath: String? = null
    ): ResponseEntity<List<WorkspaceModuleSuggestions>> {
        logger.info("Generating workspace rule suggestions for project: {}, path: {}", projectId, projectPath)

        return try {
            if (!projectPath.isNullOrBlank()) {
                ResponseEntity.ok(projectScanner.scanWorkspace(projectPath, projectId))
            } else {
                ResponseEntity.ok(projectScanner.scanProjectFromConfig(projectId, ruleRepository))
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid workspace path: ${e.message}")
            ResponseEntity.badRequest().body(emptyList())
        } catch (e: Exception) {
            logger.error("Failed to generate workspace rule suggestions: ${e.message}", e)
            ResponseEntity.internalServerError().body(emptyList())
        }
    }

    @PostMapping("/{projectId}/path")
    fun saveProjectPath(
        @PathVariable projectId: String,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Map<String, Any>> {
        val projectPath = request["projectPath"]
            ?: return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "error" to "projectPath required"))

        val config = ruleRepository.load(projectId)
            ?: RulesConfig(projectId = projectId, rules = emptyList())

        val updatedConfig = config.copy(projectPath = projectPath)
        val success = ruleRepository.save(updatedConfig)

        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "projectId" to projectId, "projectPath" to projectPath))
        } else {
            ResponseEntity.internalServerError()
                .body(mapOf("success" to false, "error" to "Failed to save project path"))
        }
    }

    @DeleteMapping("/{projectId}")
    fun deleteRules(@PathVariable projectId: String): ResponseEntity<Map<String, Any>> {
        logger.info("Deleting rules config for project: {}", projectId)

        val success = ruleRepository.delete(projectId)

        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "projectId" to projectId))
        } else {
            ResponseEntity.badRequest()
                .body(mapOf("success" to false, "error" to "Failed to delete configuration"))
        }
    }
}