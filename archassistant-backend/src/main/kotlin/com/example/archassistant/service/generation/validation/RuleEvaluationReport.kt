package com.example.archassistant.service.generation.validation

import com.example.archassistant.model.core.Violation

data class RuleEvaluationReport(
    val passed: Int,
    val evaluated: Int,
    val violations: List<Violation>
) {
    val score: Double
        get() = if (evaluated == 0) 100.0 else (passed.toDouble() / evaluated) * 100.0
}