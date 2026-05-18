package com.example.archassistant.dto.rules

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProjectPathRequest(
    val projectPath: String
)