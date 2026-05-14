package com.example.archassistant.dto.generatedfiles.request

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeneratedFilePayload(
    val packageName: String? = null,
    val className: String,
    val code: String,
    val language: String? = null
)