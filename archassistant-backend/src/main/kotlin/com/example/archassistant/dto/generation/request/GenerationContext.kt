package com.example.archassistant.dto.generation.request

/**
 * Дополнительный контекст для генерации
 */
data class GenerationContext(
    val targetPackage: String? = null,
    val existingTypes: List<String> = emptyList(),
    val codeSnippet: String? = null,
    val module: String? = null,
    val artifactKind: ArtifactKind? = null
)