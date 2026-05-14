package com.example.archassistant.controller

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.dto.health.GenerationHealthResponse
import com.example.archassistant.service.generation.GenerationFacadeService
import com.example.archassistant.service.generation.StrategyOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/generate")
@CrossOrigin(origins = ["*"])
class GenerationController(
    private val generationFacadeService: GenerationFacadeService,
    private val strategyOrchestrator: StrategyOrchestrator
) {

    @PostMapping
    fun generateCode(
        @RequestBody request: CodeGenerationRequest
    ): ResponseEntity<CodeGenerationResponse> {

        val response = generationFacadeService.generate(request)

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
                availableStrategies = strategyOrchestrator
                    .getAvailableStrategies()
                    .map { it.name },
                timestamp = java.time.LocalDateTime.now().toString()
            )
        )
    }
}