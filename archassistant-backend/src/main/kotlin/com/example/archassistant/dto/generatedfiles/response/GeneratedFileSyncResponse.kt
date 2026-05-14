package com.example.archassistant.dto.generatedfiles.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeneratedFileSyncResponse(
    val success: Boolean,
    val projectId: String,
    val projectPath: String? = null,
    val syncedFiles: Int = 0,
    val compiledSources: Int = 0,
    val overlaySourceDir: String? = null,
    val overlayClassesDir: String? = null,
    val contextRefreshed: Boolean = false,
    val warnings: List<String> = emptyList(),
    val error: String? = null
)