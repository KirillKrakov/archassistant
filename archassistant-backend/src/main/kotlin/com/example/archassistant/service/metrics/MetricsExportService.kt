package com.example.archassistant.service.metrics

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.dto.ExportFormat
import com.example.archassistant.dto.ExportRequest
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.service.metrics.export.CsvExporter
import com.example.archassistant.service.metrics.export.JsonExporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MetricsExportService(
    private val recordRepository: GenerationRecordRepository,
    private val csvExporter: CsvExporter,
    private val jsonExporter: JsonExporter,
    private val properties: ArchassistantProperties
) {

    private val logger = LoggerFactory.getLogger(MetricsExportService::class.java)

    fun export(request: ExportRequest): ExportResult {
        logger.info(
            "Exporting metrics: projectId={}, strategy={}, dateRange={}-{}, format={}",
            request.projectId, request.strategy, request.fromDate, request.toDate, request.format
        )

        val selection = fetchRecords(request)
        val maxRecords = properties.export.maxRecords.coerceAtLeast(1)

        val exportedRecords = selection.records.take(maxRecords)
        val warnings = buildList {
            addAll(selection.warnings)
            if (selection.records.size > maxRecords) {
                add("Export truncated to $maxRecords record(s) out of ${selection.records.size} matched record(s).")
            }
        }

        val exportRecords = if (request.includeViolations) {
            exportedRecords
        } else {
            exportedRecords.map { it.copy(violationsJson = null) }
        }

        val content = when (request.format) {
            ExportFormat.CSV -> csvExporter.export(exportRecords, request.includeViolations)
            ExportFormat.JSON -> jsonExporter.export(exportRecords, pretty = false)
            ExportFormat.JSON_PRETTY -> jsonExporter.export(exportRecords, pretty = true)
        }

        return ExportResult(
            format = request.format,
            recordCount = selection.records.size,
            exportedRecordCount = exportRecords.size,
            content = content,
            generatedAt = LocalDateTime.now(),
            warnings = warnings
        )
    }

    private fun fetchRecords(request: ExportRequest): RecordSelection {
        return when {
            request.projectId != null && request.strategy != null && request.fromDate != null && request.toDate != null -> {
                val records = recordRepository.findByProjectIdAndStrategyAndCreatedAtBetween(
                    request.projectId,
                    parseStrategyOrThrow(request.strategy),
                    request.fromDate,
                    request.toDate
                )
                RecordSelection(records = records)
            }

            request.projectId != null && request.strategy != null -> {
                val records = recordRepository.findByProjectIdAndStrategy(
                    request.projectId,
                    parseStrategyOrThrow(request.strategy)
                )
                RecordSelection(records = records)
            }

            request.projectId != null && request.fromDate != null && request.toDate != null -> {
                val records = recordRepository.findByProjectIdAndCreatedAtBetween(
                    request.projectId,
                    request.fromDate,
                    request.toDate
                )
                RecordSelection(records = records)
            }

            request.projectId != null -> {
                val records = recordRepository.findByProjectIdOrderByCreatedAtDesc(request.projectId)
                RecordSelection(records = records)
            }

            request.fromDate != null && request.toDate != null -> {
                val records = recordRepository.findByCreatedAtBetween(request.fromDate, request.toDate)
                RecordSelection(records = records)
            }

            else -> {
                val records = recordRepository.findAll()
                RecordSelection(records = records)
            }
        }
    }

    private fun parseStrategyOrThrow(strategy: String): com.example.archassistant.model.StrategyType {
        return runCatching {
            com.example.archassistant.model.StrategyType.valueOf(strategy.uppercase())
        }.getOrElse {
            throw IllegalArgumentException("Unknown strategy: $strategy")
        }
    }

    private data class RecordSelection(
        val records: List<GenerationRecord>,
        val warnings: List<String> = emptyList()
    )
}

data class ExportResult(
    val format: ExportFormat,
    val recordCount: Int,
    val exportedRecordCount: Int,
    val content: String,
    val generatedAt: LocalDateTime,
    val warnings: List<String> = emptyList()
)