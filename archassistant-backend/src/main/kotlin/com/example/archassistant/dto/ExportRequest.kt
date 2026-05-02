package com.example.archassistant.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Параметры экспорта метрик
 */
data class ExportRequest(
    @JsonProperty("projectId")
    val projectId: String? = null,          // Фильтр по проекту (null = все проекты)

    @JsonProperty("strategy")
    val strategy: String? = null,           // Фильтр по стратегии (PRE/POST/HYBRID)

    @JsonProperty("fromDate")
    val fromDate: LocalDateTime? = null,    // Начало периода

    @JsonProperty("toDate")
    val toDate: LocalDateTime? = null,      // Конец периода

    @JsonProperty("format")
    val format: ExportFormat = ExportFormat.CSV, // Формат вывода

    @JsonProperty("includeViolations")
    val includeViolations: Boolean = false  // Включать детали нарушений в экспорт
)

enum class ExportFormat {
    CSV,
    JSON,
    JSON_PRETTY
}