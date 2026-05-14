package com.example.archassistant.dto.metrics.request

import com.example.archassistant.model.StrategyType

data class GenerationRecordRequest(
    val id: String? = null,
    val projectId: String,
    val strategy: StrategyType,
    val prompt: String? = null,
    val generatedCode: String? = null,
    val success: Boolean,
    val scoreTotal: Double? = null,
    val scoreRulesPass: Double? = null,
    val scorePatternMatch: Double? = null,
    val scoreDependencyCorrect: Double? = null,
    val iterations: Int = 1,
    val generationTimeMs: Long = 0,
    val validationTimeMs: Long = 0,
    val violationsCount: Int = 0,
    val violationsJson: String? = null
)