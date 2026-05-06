package com.example.archassistant.dto

data class GenerationRecordDto(
    val id: String? = null,
    val projectId: String,
    val strategy: com.example.archassistant.model.StrategyType,
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