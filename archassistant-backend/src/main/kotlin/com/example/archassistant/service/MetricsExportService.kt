package com.example.archassistant.service

import com.example.archassistant.dto.ExportFormat
import com.example.archassistant.dto.ExportRequest
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.util.CsvExporter
import com.example.archassistant.util.JsonExporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MetricsExportService(
    private val recordRepository: GenerationRecordRepository,
    private val csvExporter: CsvExporter,
    private val jsonExporter: JsonExporter
) {

    private val logger = LoggerFactory.getLogger(MetricsExportService::class.java)

    /**
     * Экспорт метрик в запрошенном формате
     */
    fun export(request: ExportRequest): ExportResult {
        logger.info(
            "Exporting metrics: projectId={}, strategy={}, dateRange={}-{}, format={}",
            request.projectId, request.strategy, request.fromDate, request.toDate, request.format
        )

        // 1. Загружаем записи с фильтрацией
        val records = fetchRecords(request)

        // 2. Экспортируем в запрошенный формат
        val exportRecords = if (request.includeViolations) {
            records
        } else {
            records.map { it.copy(violationsJson = null) }
        }

        val content = when (request.format) {
            ExportFormat.CSV -> csvExporter.export(exportRecords, request.includeViolations)
            ExportFormat.JSON -> jsonExporter.export(exportRecords, pretty = false)
            ExportFormat.JSON_PRETTY -> jsonExporter.export(exportRecords, pretty = true)
        }

        // 3. Формируем результат
        return ExportResult(
            format = request.format,
            recordCount = records.size,
            content = content,
            generatedAt = LocalDateTime.now()
        )
    }

    private fun fetchRecords(request: ExportRequest): List<GenerationRecord> {
        return when {
            request.projectId != null && request.strategy != null && request.fromDate != null && request.toDate != null ->
                recordRepository.findByProjectIdAndStrategyAndCreatedAtBetween(
                    request.projectId,
                    parseStrategyOrThrow(request.strategy),
                    request.fromDate,
                    request.toDate
                )
            request.projectId != null && request.strategy != null ->
                recordRepository.findByProjectIdAndStrategy(
                    request.projectId,
                    parseStrategyOrThrow(request.strategy)
                )
            request.projectId != null && request.fromDate != null && request.toDate != null ->
                recordRepository.findByProjectIdAndCreatedAtBetween(
                    request.projectId,
                    request.fromDate,
                    request.toDate
                )
            request.projectId != null ->
                recordRepository.findByProjectIdOrderByCreatedAtDesc(request.projectId)
            request.fromDate != null && request.toDate != null ->
                recordRepository.findByCreatedAtBetween(request.fromDate, request.toDate)
            else ->
                recordRepository.findAll()
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
    val content: String,
    val generatedAt: LocalDateTime
)