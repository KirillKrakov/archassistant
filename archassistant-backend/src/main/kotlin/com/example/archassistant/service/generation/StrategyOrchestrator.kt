package com.example.archassistant.service.generation

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.model.CodeGenerationStrategy
import com.example.archassistant.model.StrategyType
import org.springframework.stereotype.Service

@Service
class StrategyOrchestrator(
    private val strategies: Map<StrategyType, CodeGenerationStrategy>
) {

    fun generate(request: CodeGenerationRequest): CodeGenerationResponse {
        val strategy = strategies[request.strategy]
            ?: throw IllegalArgumentException("Unknown strategy: ${request.strategy}")
        return strategy.generate(request)
    }

    fun getAvailableStrategies(projectId: String): List<StrategyType> = strategies.keys.toList()
}