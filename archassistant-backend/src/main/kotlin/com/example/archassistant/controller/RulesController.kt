package com.example.archassistant.controller

import com.example.archassistant.dto.rules.ProjectPathRequest
import com.example.archassistant.dto.rules.RulesConfigDto
import com.example.archassistant.dto.rules.RulesDeleteResponse
import com.example.archassistant.dto.rules.RulesSaveResponse
import com.example.archassistant.service.rules.RulesManagementService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = ["*"])
class RulesController(
    private val rulesManagementService: RulesManagementService
) {
    private val logger = LoggerFactory.getLogger(RulesController::class.java)

    @GetMapping("/{projectId}")
    fun getRules(@PathVariable projectId: String): ResponseEntity<RulesConfigDto> {
        return ResponseEntity.ok(rulesManagementService.getRules(projectId))
    }

    @PostMapping("/{projectId}")
    fun saveRules(
        @PathVariable projectId: String,
        @RequestBody config: RulesConfigDto
    ): ResponseEntity<RulesSaveResponse> {
        logger.info("Saving rules config for project: {}", projectId)

        val result = rulesManagementService.saveRules(projectId, config)
        return when {
            result.success -> ResponseEntity.ok(result)
            result.error == "Invalid configuration" -> ResponseEntity.badRequest().body(result)
            result.error == "projectId mismatch" -> ResponseEntity.badRequest().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @GetMapping("/{projectId}/suggest")
    fun suggestRules(
        @PathVariable projectId: String,
        @RequestParam projectPath: String? = null
    ): ResponseEntity<List<com.example.archassistant.service.context.WorkspaceModuleSuggestions>> {
        return try {
            ResponseEntity.ok(rulesManagementService.suggestRules(projectId, projectPath))
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid workspace path: {}", e.message)
            ResponseEntity.badRequest().body(emptyList())
        } catch (e: Exception) {
            logger.error("Failed to generate workspace rule suggestions: {}", e.message, e)
            ResponseEntity.internalServerError().body(emptyList())
        }
    }

    @PostMapping("/{projectId}/path")
    fun saveProjectPath(
        @PathVariable projectId: String,
        @RequestBody request: ProjectPathRequest
    ): ResponseEntity<com.example.archassistant.dto.rules.ProjectPathResponse> {
        val result = rulesManagementService.saveProjectPath(projectId, request.projectPath)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.internalServerError().body(result)
        }
    }

    @DeleteMapping("/{projectId}")
    fun deleteRules(@PathVariable projectId: String): ResponseEntity<RulesDeleteResponse> {
        val result = rulesManagementService.deleteRules(projectId)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }
}