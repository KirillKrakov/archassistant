package com.example.archassistant.model

data class ArchitectureDetectionResult(
    val primaryPattern: ArchitecturePattern,
    val confidence: Double,
    val scores: Map<ArchitecturePattern, Double>,
    val reasons: List<String>,
    val candidatePatterns: List<ArchitecturePattern> = emptyList(),
    val isConfident: Boolean = false
)