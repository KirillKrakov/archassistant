package com.example.archassistant.service.metrics.export

import com.example.archassistant.entity.GenerationRecord
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class CsvExporter {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun export(
        records: List<GenerationRecord>,
        includeViolations: Boolean
    ): String {

        val headers = buildList {
            addAll(
                listOf(
                    "id",
                    "projectId",
                    "strategy",
                    "prompt",
                    "generatedCode",
                    "success",
                    "scoreTotal",
                    "scoreRulesPass",
                    "scorePatternMatch",
                    "scoreDependencyCorrect",
                    "iterations",
                    "generationTimeMs",
                    "validationTimeMs",
                    "violationsCount",
                    "createdAt"
                )
            )

            if (includeViolations) {
                add("violationsJson")
            }
        }

        return buildString {
            append('\uFEFF')
            appendLine(headers.joinToString(","))

            records.forEach { record ->

                val row = buildList {

                    add(record.id)
                    add(record.projectId)
                    add(record.strategy.name)

                    add(normalizeMultiline(record.prompt))
                    add(normalizeMultiline(record.generatedCode))

                    add(record.success.toString())

                    add(record.scoreTotal?.toString() ?: "N/A")
                    add(record.scoreRulesPass?.toString() ?: "N/A")
                    add(record.scorePatternMatch?.toString() ?: "N/A")
                    add(record.scoreDependencyCorrect?.toString() ?: "N/A")

                    add(record.iterations.toString())
                    add(record.generationTimeMs.toString())
                    add(record.validationTimeMs.toString())
                    add(record.violationsCount.toString())

                    add(record.createdAt.format(dateTimeFormatter))

                    if (includeViolations) {
                        add(normalizeMultiline(record.violationsJson))
                    }
                }

                appendLine(
                    row.joinToString(",") { escapeCsv(it) }
                )
            }
        }
    }

    private fun normalizeMultiline(value: String?): String {
        if (value.isNullOrBlank()) {
            return ""
        }

        return sanitizeForSpreadsheet(
            value
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
        )
    }

    private fun sanitizeForSpreadsheet(value: String): String {
        return if (
            value.startsWith("=") ||
            value.startsWith("+") ||
            value.startsWith("-") ||
            value.startsWith("@")
        ) {
            "'$value"
        } else {
            value
        }
    }

    private fun escapeCsv(value: String): String {

        val escaped = value.replace("\"", "\"\"")

        return "\"$escaped\""
    }
}