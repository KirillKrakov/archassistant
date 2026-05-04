package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.model.CodeGenerationStrategy
import com.example.archassistant.model.StrategyType
import org.springframework.stereotype.Service

/**
 * Оркестратор выбора стратегии генерации кода
 * делегирует запрос соответствующей реализации CodeGenerationStrategy
 */
@Service
class StrategyOrchestrator(
    private val strategies: Map<StrategyType, CodeGenerationStrategy>
) {

    /**
     * Генерация кода с выбранной стратегией
     */
    fun generate(request: CodeGenerationRequest): CodeGenerationResponse {
        val strategy = strategies[request.strategy]
            ?: throw IllegalArgumentException("Unknown strategy: ${request.strategy}")

        return strategy.generate(request)
    }

    /**
     * Получение доступных стратегий для данного проекта
     */
    fun getAvailableStrategies(projectId: String): List<StrategyType> {
        // В будущем можно фильтровать стратегии по возможностям проекта
        return strategies.keys.toList()
    }
}