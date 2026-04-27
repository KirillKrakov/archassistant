package com.example.archassistant.config

import com.example.archassistant.model.CodeGenerationStrategy
import com.example.archassistant.model.StrategyType
import com.example.archassistant.service.*
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
        // postStrategy: PostGenerationStrategy, будет добавлено на Этапе 10
        // hybridStrategy: HybridGenerationStrategy, будет добавлено на Этапе 11
    ): Map<StrategyType, CodeGenerationStrategy> {
        return mapOf(
            StrategyType.PRE to preStrategy
            // StrategyType.POST to postStrategy,
            // StrategyType.HYBRID to hybridStrategy
        )
    }
}