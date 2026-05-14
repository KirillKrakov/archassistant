package com.example.archassistant.controller

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.service.generation.StrategyOrchestrator
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/generate")
@CrossOrigin(origins = ["*"])
class GenerationController(
    private val strategyOrchestrator: StrategyOrchestrator,
    private val metricsRepository: GenerationRecordRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(GenerationController::class.java)

    @PostMapping
    fun generateCode(@RequestBody request: CodeGenerationRequest): ResponseEntity<CodeGenerationResponse> {
        logger.info(
            "Received generation request: strategy={}, projectId={}, promptLength={}",
            request.strategy, request.projectId, request.prompt.length
        )

        if (request.prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }

        val response = strategyOrchestrator.generate(request)
        saveMetrics(request.projectId, request.prompt, response)

        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        val availableStrategies = strategyOrchestrator.getAvailableStrategies()
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "availableStrategies" to availableStrategies.map { it.name },
                "timestamp" to LocalDateTime.now().toString()
            )
        )
    }

    private fun saveMetrics(projectId: String, prompt: String, response: CodeGenerationResponse) {
        logger.info("Received generated code: {}", response.data?.code)

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
            metricsRepository.saveAndFlush(record)
        } catch (e: Exception) {
            logger.warn("Failed to save metrics: ", e)
        }
    }
}