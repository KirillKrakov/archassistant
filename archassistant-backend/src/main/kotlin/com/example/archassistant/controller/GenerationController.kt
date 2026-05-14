package com.example.archassistant.controller

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.dto.health.GenerationHealthResponse
import com.example.archassistant.service.generation.StrategyOrchestrator
import com.example.archassistant.service.metrics.GenerationMetricsService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/generate")
@CrossOrigin(origins = ["*"])
class GenerationController(
    private val strategyOrchestrator: StrategyOrchestrator,
    private val generationMetricsService: GenerationMetricsService
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
        generationMetricsService.recordGeneration(request.projectId, request.prompt, response)

        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<GenerationHealthResponse> {
        return ResponseEntity.ok(
            GenerationHealthResponse(
                status = "UP",
                availableStrategies = strategyOrchestrator.getAvailableStrategies().map { it.name },
                timestamp = java.time.LocalDateTime.now().toString()
            )
        )
    }
}