package com.example.archassistant.controller

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.dto.ErrorDetails
import com.example.archassistant.dto.ResponseMetadata
import com.example.archassistant.service.StrategyOrchestrator
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Контроллер для генерации кода с архитектурным контролем
 *
 * Endpoints:
 * POST /api/generate - Основная точка входа для генерации кода
 */
@RestController
@RequestMapping("/api/generate")
@CrossOrigin(origins = ["*"]) // Для удобства тестирования из Postman/браузера (поменять в production)
class GenerationController(
    private val strategyOrchestrator: StrategyOrchestrator,
    private val metricsRepository: GenerationRecordRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(GenerationController::class.java)

    /**
     * Генерация кода с выбранной стратегией архитектурного контроля
     */
    @PostMapping
    fun generateCode(@RequestBody request: CodeGenerationRequest): ResponseEntity<CodeGenerationResponse> {
        logger.info("Received generation request: strategy={}, projectId={}, promptLength={}",
            request.strategy, request.projectId, request.prompt.length)

        if (request.prompt.isBlank()) {
            return ResponseEntity.badRequest().body(
                CodeGenerationResponse(
                    success = false,
                    data = null,
                    error = ErrorDetails(
                        code = "INVALID_PROMPT",
                        message = "Prompt cannot be empty"
                    ),
                    metadata = ResponseMetadata(
                        generationTimeMs = 0,
                        validationTimeMs = 0,
                        totalTimeMs = 0
                    )
                )
            )
        }

        return try {
            val response = strategyOrchestrator.generate(request)

            saveMetricsAsync(request.projectId, request.prompt, response)

            when {
                response.success -> ResponseEntity.ok(response)
                else -> ResponseEntity.badRequest().body(response)
            }
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                CodeGenerationResponse(
                    success = false,
                    data = null,
                    error = ErrorDetails(
                        code = "INVALID_REQUEST",
                        message = e.message ?: "Invalid request parameters"
                    ),
                    metadata = ResponseMetadata(0, 0, 0)
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                CodeGenerationResponse(
                    success = false,
                    data = null,
                    error = ErrorDetails(
                        code = "INTERNAL_ERROR",
                        message = "Internal server error: ${e.message}"
                    ),
                    metadata = ResponseMetadata(0, 0, 0)
                )
            )
        }
    }

    /**
     * Health check для генерации (проверка доступности стратегий)
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        val availableStrategies = strategyOrchestrator.getAvailableStrategies("")
        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "availableStrategies" to availableStrategies.map { it.name },
            "timestamp" to java.time.LocalDateTime.now().toString()
        ))
    }

    private fun saveMetricsAsync(projectId: String, prompt: String, response: CodeGenerationResponse) {
        try {
            val violations = response.data?.score?.violations.orEmpty()
            val record = GenerationRecord(
                projectId = projectId,
                strategy = response.data?.strategy ?: StrategyType.HYBRID,
                prompt = prompt,
                generatedCode = response.data?.code,
                success = response.success,
                scoreTotal = response.data?.score?.total,
                scoreRulesPass = response.data?.score?.rulesPass,
                scorePatternMatch = response.data?.score?.patternMatch,
                scoreDependencyCorrect = response.data?.score?.dependencyCorrect,
                iterations = response.data?.iterations ?: 1,
                generationTimeMs = response.metadata.generationTimeMs,
                validationTimeMs = response.metadata.validationTimeMs,
                violationsCount = violations.size,
                violationsJson = if (violations.isNotEmpty()) objectMapper.writeValueAsString(violations) else null
            )
            metricsRepository.save(record)
        } catch (e: Exception) {
            logger.warn("Failed to save metrics: ${e.message}")
        }
    }
}