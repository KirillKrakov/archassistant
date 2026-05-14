package com.example.archassistant.dto.rules

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RulesDeleteResponse(
    val success: Boolean,
    val projectId: String? = null,
    val error: String? = null
)