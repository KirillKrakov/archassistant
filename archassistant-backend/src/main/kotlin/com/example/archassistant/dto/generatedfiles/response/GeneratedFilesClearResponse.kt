package com.example.archassistant.dto.generatedfiles.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeneratedFilesClearResponse(
    val success: Boolean,
    val projectId: String? = null,
    val contextRefreshed: Boolean? = null,
    val error: String? = null
)