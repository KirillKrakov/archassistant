package com.example.archassistant.model.generation

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.model.ProjectContextSnapshot
import com.example.archassistant.model.rules.ArchitecturalRule

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