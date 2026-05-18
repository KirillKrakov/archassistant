package com.example.archassistant.dto.health

data class GenerationHealthResponse(
    val status: String,
    val availableStrategies: List<String>,
    val timestamp: String
)