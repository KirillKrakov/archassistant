package com.example.archassistant.model.generation

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.model.core.StrategyType

/**
 * Интерфейс стратегии архитектурного контроля при генерации кода
 * Реализуется для Pre, Post и Hybrid стратегий
 */
interface CodeGenerationStrategy {

    /**
     * Генерация кода с применением стратегии
     *
     * @param request Запрос на генерацию
     * @return Ответ с кодом, метриками и метаданными
     */
    fun generate(request: CodeGenerationRequest): CodeGenerationResponse

    /**
     * Тип стратегии, реализуемой этим классом
     */
    val strategyType: StrategyType
}