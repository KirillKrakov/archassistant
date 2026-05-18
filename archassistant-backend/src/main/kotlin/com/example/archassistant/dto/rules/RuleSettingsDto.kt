package com.example.archassistant.dto.rules

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RuleSettingsDto(
    @JsonProperty("max_iterations")
    val maxIterations: Int = 3,

    @JsonProperty("timeout_seconds")
    val timeoutSeconds: Int = 30,

    @JsonProperty("default_strategy")
    val defaultStrategy: String = "HYBRID",

    @JsonProperty("fail_on_critical")
    val failOnCritical: Boolean = true,

    @JsonProperty("auto_fix_naming")
    val autoFixNaming: Boolean = false
)