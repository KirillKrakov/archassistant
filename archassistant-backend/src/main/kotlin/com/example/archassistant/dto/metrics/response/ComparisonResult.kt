package com.example.archassistant.dto.metrics.response

import com.example.archassistant.model.StrategyType
import java.util.Locale

data class ComparisonResult(
    val projectId: String? = null,
    val strategies: Map<StrategyType, StrategyComparison>,
    val recommendation: Recommendation? = null,
    val comparedAt: String = java.time.LocalDateTime.now().toString()
)