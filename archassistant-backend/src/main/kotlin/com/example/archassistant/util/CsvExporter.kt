package com.example.archassistant.util

import com.example.archassistant.model.GenerationRecord
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class CsvExporter {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Экспорт записей в CSV-формат
     */
    fun export(records: List<GenerationRecord>, includeViolations: Boolean): String {
        val headers = listOf(
            "id", "projectId", "strategy", "prompt", "generatedCode", "success", "scoreTotal", "scoreRulesPass",
            "scorePatternMatch", "scoreDependencyCorrect", "iterations", "generationTimeMs",
            "validationTimeMs", "violationsCount", "createdAt"
        ) + if (includeViolations) listOf("violationsJson") else emptyList()

        val rows = records.map { record ->
            val base = listOf(
                record.id,
                record.projectId,
                record.strategy.name,
                record.prompt ?: "",
                record.generatedCode ?: "",
                record.success.toString(),
                record.scoreTotal?.toString() ?: "N/A",
                record.scoreRulesPass?.toString() ?: "N/A",
                record.scorePatternMatch?.toString() ?: "N/A",
                record.scoreDependencyCorrect?.toString() ?: "N/A",
                record.iterations.toString(),
                record.generationTimeMs.toString(),
                record.validationTimeMs.toString(),
                record.violationsCount.toString(),
                record.createdAt.format(dateTimeFormatter)
            )
            if (includeViolations) {
                base + listOf(record.violationsJson?.let { escapeCsv(it) } ?: "N/A")
            } else {
                base
            }
        }

        return buildString {
            appendLine(headers.joinToString(","))
            rows.forEach { row ->
                appendLine(row.joinToString(",") { escapeCsv(it) })
            }
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}