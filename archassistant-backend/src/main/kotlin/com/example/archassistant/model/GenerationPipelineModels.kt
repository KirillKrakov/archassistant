package com.example.archassistant.model

import com.example.archassistant.dto.generation.request.CodeGenerationRequest

data class PreparedGenerationRequest(
    val projectContext: ProjectContextSnapshot,
    val rules: List<ArchitecturalRule>,
    val normalizedTargetPackage: String?,
    val normalizedExpectedClassName: String?,
    val normalizedExistingTypes: List<String>
) {
    fun promptContext(request: CodeGenerationRequest): String {
        return projectContext.promptContext(
            requestText = request.prompt,
            targetPackage = normalizedTargetPackage,
            expectedClassName = normalizedExpectedClassName,
            existingTypes = normalizedExistingTypes
        )
    }

    fun languageHint(): String? = projectContext.preferredLanguageHint()
}

data class GenerationPrompt(
    val systemPrompt: String,
    val userPrompt: String
)

data class GenerationAttemptResult(
    val rawCode: String,
    val generationTimeMs: Long
)

data class GenerationValidationResult(
    val generatedCode: String,
    val primaryTypeName: String?,
    val score: ComplianceScore?,
    val violations: List<Violation>,
    val validationTimeMs: Long
)