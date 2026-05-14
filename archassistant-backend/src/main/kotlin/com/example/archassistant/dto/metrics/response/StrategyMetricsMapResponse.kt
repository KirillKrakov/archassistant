package com.example.archassistant.dto.metrics.response

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StrategyMetricsMapResponse(
    @get:JsonIgnore
    val strategies: Map<String, StrategyMetrics> = emptyMap()
) {
    @JsonAnyGetter
    fun anyStrategies(): Map<String, Map<String, Any?>> =
        strategies.mapValues { (_, metric) -> metric.toMap() }
}