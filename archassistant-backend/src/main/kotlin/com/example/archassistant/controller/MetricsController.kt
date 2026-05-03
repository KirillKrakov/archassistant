package com.example.archassistant.controller

import com.example.archassistant.dto.ExportRequest
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.service.MetricsComparisonService
import com.example.archassistant.service.MetricsExportService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import com.example.archassistant.dto.ExportFormat
import com.example.archassistant.dto.GenerationRecordDto

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = ["*"])
class MetricsController(
    private val metricsRepository: GenerationRecordRepository,
    private val exportService: MetricsExportService,
    private val comparisonService: MetricsComparisonService
) {

    private companion object {
        const val RECENT_HISTORY_LIMIT = 50
    }

    private val logger = LoggerFactory.getLogger(MetricsController::class.java)

    // ─────────────────────────────────────────────────────────────────
    // Существующие эндпоинты (без изменений, для совместимости)
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/strategies")
    fun compareStrategies(
        @RequestParam(required = false) projectId: String?
    ): ResponseEntity<Map<String, Any>> {
        val metrics = if (projectId.isNullOrBlank()) {
            metricsRepository.getAllMetrics()
        } else {
            metricsRepository.getMetricsByProject(projectId)
        }

        val result = metrics.associate { it.strategy.name to it.toMap() }

        return ResponseEntity.ok(result)
    }

    @GetMapping("/{projectId}")
    fun getProjectMetrics(@PathVariable projectId: String): ResponseEntity<Map<String, Any?>> {
        val totalGenerations = metricsRepository.countByProjectId(projectId)
        val history = metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId).take(RECENT_HISTORY_LIMIT)
        val scoreTotals = history.mapNotNull { it.scoreTotal }
        val avgScore = if (scoreTotals.isNotEmpty()) scoreTotals.average() else null
        val lastGeneration = history.firstOrNull()?.createdAt

        return ResponseEntity.ok(mapOf(
            "projectId" to projectId,
            "totalGenerations" to totalGenerations,
            "avgScore" to avgScore?.let { "%.2f".format(it).toDouble() },
            "lastGeneration" to lastGeneration?.toString(),
            "recentHistory" to history.map { record ->
                mapOf<String, Any?>(
                    "strategy" to record.strategy.name,
                    "score" to record.scoreTotal,
                    "iterations" to record.iterations,
                    "success" to record.success,
                    "timestamp" to record.createdAt.toString()
                )
            }
        ))
    }

    @PostMapping("/record")
    fun saveGenerationRecord(@RequestBody record: GenerationRecordDto): ResponseEntity<Map<String, Any>> {
        logger.info("Saving generation record: strategy={}, projectId={}, score={}",
            record.strategy, record.projectId, record.scoreTotal)

        return try {
            val entity = GenerationRecord(
                id = record.id ?: java.util.UUID.randomUUID().toString(),
                projectId = record.projectId,
                strategy = record.strategy,
                success = record.success,
                scoreTotal = record.scoreTotal,
                scoreRulesPass = record.scoreRulesPass,
                scorePatternMatch = record.scorePatternMatch,
                scoreDependencyCorrect = record.scoreDependencyCorrect,
                iterations = record.iterations,
                generationTimeMs = record.generationTimeMs,
                validationTimeMs = record.validationTimeMs,
                violationsCount = record.violationsCount,
                violationsJson = record.violationsJson
            )

            metricsRepository.save(entity)

            ResponseEntity.ok(mapOf("success" to true, "recordId" to entity.id))
        } catch (e: Exception) {
            logger.error("Failed to save generation record: ${e.message}", e)
            return ResponseEntity.internalServerError()
                .body(mapOf<String, Any>("success" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

    /**
     * Экспорт метрик в CSV/JSON
     */
    @PostMapping("/export")
    fun exportMetrics(@RequestBody request: ExportRequest): ResponseEntity<Any> {
        try {
            val result = exportService.export(request)

            val contentType = when (request.format) {
                ExportFormat.CSV -> "text/csv"
                ExportFormat.JSON, ExportFormat.JSON_PRETTY -> "application/json"
            }

            val filename = "archassistant-metrics-${request.projectId ?: "all"}-${result.generatedAt}.${request.format.name.lowercase()}"

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                .body(result.content)

        } catch (e: Exception) {
            logger.error("Export failed: ${e.message}", e)
            return ResponseEntity.internalServerError()
                .body(mapOf("error" to "Export failed: ${e.message}"))
        }
    }

    /**
     * Сравнение стратегий с рекомендацией
     */
    @GetMapping("/compare")
    fun compareStrategiesDetailed(
        @RequestParam(required = false) projectId: String?
    ): ResponseEntity<Any> {
        try {
            val result = comparisonService.compare(projectId)
            return ResponseEntity.ok(result)
        } catch (e: Exception) {
            logger.error("Comparison failed: ${e.message}", e)
            return ResponseEntity.internalServerError()
                .body(mapOf("error" to "Comparison failed: ${e.message}"))
        }
    }

    /**
     * История генераций с пагинацией
     */
    @GetMapping("/{projectId}/history")
    fun getGenerationHistory(
        @PathVariable projectId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) strategy: String?,
        @RequestParam(required = false) fromDate: LocalDateTime?,
        @RequestParam(required = false) toDate: LocalDateTime?
    ): ResponseEntity<Map<String, Any>> {
        try {
            val records = when {
                strategy != null && fromDate != null && toDate != null ->
                    metricsRepository.findByProjectIdAndStrategyAndCreatedAtBetween(
                        projectId,
                        com.example.archassistant.model.StrategyType.valueOf(strategy),
                        fromDate,
                        toDate
                    )
                strategy != null -> {
                    val strategyType = parseStrategyOrNull(strategy)
                        ?: return ResponseEntity.badRequest().body(mapOf("error" to "Unknown strategy: $strategy"))

                    metricsRepository.findByProjectIdAndStrategy(projectId, strategyType)
                }
                fromDate != null && toDate != null ->
                    metricsRepository.findByProjectIdAndCreatedAtBetween(projectId, fromDate, toDate)
                else ->
                    metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            }

            // Пагинация
            val paginated = records.drop(page * size).take(size)
            val total = records.size
            val totalPages = (total + size - 1) / size

            return ResponseEntity.ok(mapOf(
                "projectId" to projectId,
                "page" to page,
                "size" to size,
                "totalRecords" to total,
                "totalPages" to totalPages,
                "records" to paginated.map { record ->
                    mapOf(
                        "id" to record.id,
                        "strategy" to record.strategy.name,
                        "success" to record.success,
                        "score" to record.scoreTotal,
                        "iterations" to record.iterations,
                        "generationTimeMs" to record.generationTimeMs,
                        "validationTimeMs" to record.validationTimeMs,
                        "violationsCount" to record.violationsCount,
                        "createdAt" to record.createdAt
                    )
                }
            ))
        } catch (e: Exception) {
            logger.error("History fetch failed: ${e.message}", e)
            return ResponseEntity.internalServerError()
                .body(mapOf("error" to "History fetch failed: ${e.message}"))
        }
    }

    private fun parseStrategyOrNull(value: String?): com.example.archassistant.model.StrategyType? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            com.example.archassistant.model.StrategyType.valueOf(value.uppercase())
        }.getOrNull()
    }
}