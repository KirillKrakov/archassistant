package com.example.archassistant.config

import com.example.archassistant.model.core.StrategyType
import com.example.archassistant.model.generation.CodeGenerationStrategy
import com.example.archassistant.service.generation.strategy.HybridGenerationStrategy
import com.example.archassistant.service.generation.strategy.PostGenerationStrategy
import com.example.archassistant.service.generation.strategy.PreGenerationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Конфигурация стратегий генерации кода
 * регистрирует реализации CodeGenerationStrategy в Map для StrategyOrchestrator
 */
@Configuration
class StrategyConfig {

    @Bean
    fun strategyMap(
        preStrategy: PreGenerationStrategy,
        postStrategy: PostGenerationStrategy,
        hybridStrategy: HybridGenerationStrategy
    ): Map<StrategyType, CodeGenerationStrategy> {
        return mapOf(
            StrategyType.PRE to preStrategy,
            StrategyType.POST to postStrategy,
            StrategyType.HYBRID to hybridStrategy
        )
    }
}