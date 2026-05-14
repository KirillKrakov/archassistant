package com.example.archassistant.dto.metrics.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SaveGenerationRecordResponse(
    val success: Boolean,
    val recordId: String? = null,
    val error: String? = null
)