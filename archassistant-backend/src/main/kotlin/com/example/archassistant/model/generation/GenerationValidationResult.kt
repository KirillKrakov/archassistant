package com.example.archassistant.model.generation

import com.example.archassistant.model.Violation
import com.example.archassistant.model.core.ComplianceScore

data class GenerationValidationResult(
    val generatedCode: String,
    val primaryTypeName: String?,
    val score: ComplianceScore?,
    val violations: List<Violation>,
    val validationTimeMs: Long
)