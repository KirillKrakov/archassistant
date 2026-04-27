package com.example.archassistant.model

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse

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