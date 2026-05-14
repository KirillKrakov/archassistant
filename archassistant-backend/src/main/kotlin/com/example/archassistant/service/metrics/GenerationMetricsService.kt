package com.example.archassistant.service.metrics

import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GenerationMetricsService(
    private val metricsRepository: GenerationRecordRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(GenerationMetricsService::class.java)

    fun recordGeneration(
        projectId: String,
        prompt: String,
        response: CodeGenerationResponse
    ) {
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
            logger.warn("Failed to save generation metrics: ", e)
        }
    }
}