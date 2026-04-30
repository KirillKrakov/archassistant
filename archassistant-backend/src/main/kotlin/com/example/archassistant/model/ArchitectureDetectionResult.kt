package com.example.archassistant.model

data class ArchitectureDetectionResult(
    val primaryPattern: ArchitecturePattern = ArchitecturePattern.UNKNOWN,
    val confidence: Double = 0.0,
    val scores: Map<ArchitecturePattern, Double> = emptyMap(),
    val reasons: List<String> = emptyList()
)