package com.example.archassistant.dto.metrics.response

import com.example.archassistant.model.StrategyType

data class Recommendation(
    val bestStrategy: StrategyType,
    val reason: String,
    val confidence: Double
)