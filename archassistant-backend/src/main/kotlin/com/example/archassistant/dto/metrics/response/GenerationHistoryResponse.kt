package com.example.archassistant.dto.metrics.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GenerationHistoryResponse(
    val projectId: String,
    val page: Int,
    val size: Int,
    val totalRecords: Long,
    val totalPages: Int,
    val records: List<GenerationHistoryItem> = emptyList()
)