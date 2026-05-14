package com.example.archassistant.service.metrics

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.dto.metrics.request.ExportFormat
import com.example.archassistant.dto.metrics.request.ExportRequest
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.service.metrics.export.CsvExporter
import com.example.archassistant.service.metrics.export.JsonExporter
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
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

        val matchedCount = selection.size
        val exportRecords = selection.take(maxRecords)

        val warnings = buildList {
            if (matchedCount > maxRecords) {
                add("Export truncated to $maxRecords record(s) out of $matchedCount matched record(s).")
            }
        }

        val sanitizedRecords = if (request.includeViolations) {
            exportRecords
        } else {
            exportRecords.map { it.copy(violationsJson = null) }
        }

        val content = when (request.format) {
            ExportFormat.CSV -> csvExporter.export(sanitizedRecords, request.includeViolations)
            ExportFormat.JSON -> jsonExporter.export(sanitizedRecords, pretty = false)
            ExportFormat.JSON_PRETTY -> jsonExporter.export(sanitizedRecords, pretty = true)
        }

        return ExportResult(
            format = request.format,
            recordCount = matchedCount,
            exportedRecordCount = sanitizedRecords.size,
            content = content,
            generatedAt = LocalDateTime.now(),
            warnings = warnings
        )
    }

    private fun fetchRecords(request: ExportRequest): List<GenerationRecord> {
        val maxRecords = properties.export.maxRecords.coerceAtLeast(1)
        val pageRequest = PageRequest.of(0, maxRecords)

        return when {
            request.projectId != null && request.strategy != null && request.fromDate != null && request.toDate != null ->
                recordRepository.findByProjectIdAndStrategyAndCreatedAtBetween(
                    request.projectId,
                    parseStrategyOrThrow(request.strategy),
                    request.fromDate,
                    request.toDate,
                    pageRequest
                ).content

            request.projectId != null && request.strategy != null ->
                recordRepository.findByProjectIdAndStrategy(
                    request.projectId,
                    parseStrategyOrThrow(request.strategy),
                    pageRequest
                ).content

            request.projectId != null && request.fromDate != null && request.toDate != null ->
                recordRepository.findByProjectIdAndCreatedAtBetween(
                    request.projectId,
                    request.fromDate,
                    request.toDate,
                    pageRequest
                ).content

            request.projectId != null ->
                recordRepository.findByProjectIdOrderByCreatedAtDesc(request.projectId, pageRequest).content

            request.fromDate != null && request.toDate != null ->
                recordRepository.findByCreatedAtBetween(request.fromDate, request.toDate, pageRequest).content

            else ->
                recordRepository.findAll(pageRequest).content
        }
    }

    private fun parseStrategyOrThrow(strategy: String): com.example.archassistant.model.StrategyType {
        return runCatching {
            com.example.archassistant.model.StrategyType.valueOf(strategy.uppercase())
        }.getOrElse {
            throw IllegalArgumentException("Unknown strategy: $strategy")
        }
    }
}

data class ExportResult(
    val format: ExportFormat,
    val recordCount: Int,
    val exportedRecordCount: Int,
    val content: String,
    val generatedAt: LocalDateTime,
    val warnings: List<String> = emptyList()
)