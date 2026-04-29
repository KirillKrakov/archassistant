package com.example.archassistant.controller

import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.RulesConfig
import com.example.archassistant.service.ProjectStructureScanner
import com.example.archassistant.service.RuleTemplateEngine
import com.example.archassistant.service.YamlRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для управления архитектурными правилами
 *
 * Endpoints:
 * GET    /api/rules/{projectId}           - Получить конфигурацию правил
 * POST   /api/rules/{projectId}           - Сохранить конфигурацию правил
 * GET    /api/rules/{projectId}/suggest   - Получить предложения правил на основе структуры
 * DELETE /api/rules/{projectId}           - Удалить конфигурацию правил
 */
@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = ["*"])
class RulesController(
    private val ruleRepository: YamlRuleRepository,
    private val templateEngine: RuleTemplateEngine,
    private val projectScanner: ProjectStructureScanner
) {

    private val logger = LoggerFactory.getLogger(RulesController::class.java)

    /**
     * Получить конфигурацию правил для проекта
     */
    @GetMapping("/{projectId}")
    fun getRules(@PathVariable projectId: String): ResponseEntity<Any> {
        val config = ruleRepository.load(projectId)

        return if (config != null) {
            ResponseEntity.ok(config)
        } else {
            ResponseEntity.ok(
                RulesConfig(
                    projectId = projectId,
                    rules = emptyList()
                )
            )
        }
    }

    /**
     * Сохранить конфигурацию правил для проекта
     */
    @PostMapping("/{projectId}")
    fun saveRules(
        @PathVariable projectId: String,
        @RequestBody config: RulesConfig
    ): ResponseEntity<Map<String, Any>> {
        logger.info("Saving rules config for project: {}", projectId)

        // Валидация: projectId в теле должен совпадать с путём
        if (config.projectId != projectId) {
            return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "error" to "projectId mismatch"))
        }

        // Валидация конфигурации
        val validationResult = ruleRepository.validate(config)
        if (!validationResult.passed) {
            return ResponseEntity.badRequest()
                .body(mapOf(
                    "success" to false,
                    "error" to "Invalid configuration",
                    "violations" to validationResult.violations
                ))
        }

        val success = ruleRepository.save(config)

        return if (success) {
            logger.info("Rules config saved successfully for {}", projectId)
            ResponseEntity.ok(mapOf("success" to true, "projectId" to projectId))
        } else {
            logger.error("Failed to save rules config for {}", projectId)
            ResponseEntity.internalServerError()
                .body(mapOf("success" to false, "error" to "Failed to save configuration"))
        }
    }

    /**
     * Получить предложения правил на основе АНАЛИЗА РЕАЛЬНОЙ структуры проекта
     *
     * @param projectId ID проекта
     * @param projectPath Путь к директории проекта (опционально, если не указан - ищем в конфиге)
     * @return Список предложенных правил
     */
    @GetMapping("/{projectId}/suggest")
    fun suggestRules(
        @PathVariable projectId: String,
        @RequestParam(required = false) projectPath: String? = null
    ): ResponseEntity<List<ArchitecturalRule>> {
        logger.info("Generating rule suggestions for project: {}, path: {}", projectId, projectPath ?: "from config")

        return try {
            // Шаг 1: Получить структуру проекта (сканирование или из конфига)
            val structure = if (!projectPath.isNullOrBlank()) {
                // Явно указан путь - сканируем
                projectScanner.scanProject(projectPath, projectId)
            } else {
                // Путь не указан - пробуем из конфига
                projectScanner.scanProjectFromConfig(projectId, ruleRepository)
            }

            // Шаг 2: Если структура не получена - вернуть пустой список с предупреждением
            if (structure == null) {
                logger.warn("Could not scan project structure for {}", projectId)
                return ResponseEntity.ok(emptyList())
            }

            // Шаг 3: Использовать RuleTemplateEngine для предложения правил (Этап 5!)
            val suggestions = templateEngine.suggestRules(structure)

            logger.info("Generated {} rule suggestions for pattern: {}",
                suggestions.size, structure.architecturePattern)

            ResponseEntity.ok(suggestions)

        } catch (e: IllegalArgumentException) {
            logger.error("Invalid project path: ${e.message}")
            ResponseEntity.badRequest()
                .body(emptyList())
        } catch (e: Exception) {
            logger.error("Failed to generate rule suggestions: ${e.message}", e)
            ResponseEntity.internalServerError()
                .body(emptyList())
        }
    }

    /**
     * Сохранить путь к проекту в конфигурацию (для будущего сканирования)
     */
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

        // Сохранить путь в settings (нужно расширить RulesConfig или использовать доп. поле)
        // Для простоты - создаём новую конфигурацию с путём
        val updatedConfig = config.copy(
            projectPath = projectPath
        )

        val success = ruleRepository.save(updatedConfig)

        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "projectId" to projectId, "projectPath" to projectPath))
        } else {
            ResponseEntity.internalServerError()
                .body(mapOf("success" to false, "error" to "Failed to save project path"))
        }
    }

    /**
     * Удалить конфигурацию правил для проекта
     */
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