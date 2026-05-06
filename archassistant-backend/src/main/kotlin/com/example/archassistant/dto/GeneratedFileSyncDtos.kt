package com.example.archassistant.dto

data class GeneratedFileSyncRequest(
    val projectPath: String? = null,
    val files: List<GeneratedFilePayload> = emptyList()
)

data class GeneratedFilePayload(
    val packageName: String? = null,
    val className: String,
    val code: String,
    val language: String? = null
)

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