package com.example.archassistant.dto.generatedfiles.request

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeneratedFileSyncRequest(
    val projectPath: String? = null,
    val files: List<GeneratedFilePayload> = emptyList()
)