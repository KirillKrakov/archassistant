package com.example.archassistant.controller

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.dto.ExportFormat
import com.example.archassistant.dto.ExportRequest
import com.example.archassistant.dto.GenerationRecordDto
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.service.metrics.MetricsComparisonService
import com.example.archassistant.service.metrics.MetricsExportService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = ["*"])
class MetricsController(
    private val metricsRepository: GenerationRecordRepository,
    private val exportService: MetricsExportService,
    private val comparisonService: MetricsComparisonService,
    private val properties: ArchassistantProperties
) {

    private companion object {
        const val RECENT_HISTORY_LIMIT = 50
    }

    private val logger = LoggerFactory.getLogger(MetricsController::class.java)

    @GetMapping("/strategies")
    fun compareStrategies(
        @RequestParam(required = false) projectId: String?
    ): ResponseEntity<Map<String, Any>> {
        val threshold = properties.compliance.threshold

        val metrics = if (projectId.isNullOrBlank()) {
            metricsRepository.getAllMetrics(threshold)
        } else {
            metricsRepository.getMetricsByProject(projectId, threshold)
        }

        val result = metrics.associate { it.strategy.name to it.toMap() }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{projectId}")
    fun getProjectMetrics(@PathVariable projectId: String): ResponseEntity<Map<String, Any?>> {
        val fullHistory = metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
        val recentHistory = fullHistory.take(RECENT_HISTORY_LIMIT)

        val totalGenerations = fullHistory.size.toLong()
        val successfulGenerations = fullHistory.count { it.success }.toLong()
        val failedGenerations = totalGenerations - successfulGenerations

        val scoreTotals = fullHistory.mapNotNull { it.scoreTotal }
        val avgScore = if (scoreTotals.isNotEmpty()) scoreTotals.average() else null

        val avgIterations = if (fullHistory.isNotEmpty()) {
            fullHistory.map { it.iterations.toDouble() }.average()
        } else {
            null
        }

        val avgGenerationTimeMs = if (fullHistory.isNotEmpty()) {
            fullHistory.map { it.generationTimeMs.toDouble() }.average()
        } else {
            null
        }

        val avgValidationTimeMs = if (fullHistory.isNotEmpty()) {
            fullHistory.map { it.validationTimeMs.toDouble() }.average()
        } else {
            null
        }

        val avgTotalTimeMs = if (fullHistory.isNotEmpty()) {
            fullHistory.map { (it.generationTimeMs + it.validationTimeMs).toDouble() }.average()
        } else {
            null
        }

        val successRate = if (totalGenerations > 0) {
            successfulGenerations.toDouble() / totalGenerations.toDouble()
        } else {
            null
        }

        val lastGeneration = fullHistory.firstOrNull()?.createdAt

        return ResponseEntity.ok(
            mapOf(
                "projectId" to projectId,
                "totalGenerations" to totalGenerations,
                "successfulGenerations" to successfulGenerations,
                "failedGenerations" to failedGenerations,
                "avgScore" to avgScore?.let { "%.2f".format(it).toDouble() },
                "avgIterations" to avgIterations?.let { "%.2f".format(it).toDouble() },
                "avgGenerationTimeMs" to avgGenerationTimeMs?.let { "%.2f".format(it).toDouble() },
                "avgValidationTimeMs" to avgValidationTimeMs?.let { "%.2f".format(it).toDouble() },
                "avgTotalTimeMs" to avgTotalTimeMs?.let { "%.2f".format(it).toDouble() },
                "successRate" to successRate?.let { "%.2f".format(it).toDouble() },
                "successRatePercent" to successRate?.let { "%.2f".format(it * 100.0).toDouble() },
                "lastGeneration" to lastGeneration?.toString(),
                "recentHistory" to recentHistory.map { record ->
                    mapOf<String, Any?>(
                        "strategy" to record.strategy.name,
                        "score" to record.scoreTotal,
                        "iterations" to record.iterations,
                        "success" to record.success,
                        "generationTimeMs" to record.generationTimeMs,
                        "validationTimeMs" to record.validationTimeMs,
                        "totalTimeMs" to (record.generationTimeMs + record.validationTimeMs),
                        "timestamp" to record.createdAt.toString(),
                        "prompt" to record.prompt,
                        "generatedCode" to record.generatedCode
                    )
                }
            )
        )
    }

    @PostMapping("/record")
    fun saveGenerationRecord(@RequestBody record: GenerationRecordDto): ResponseEntity<Map<String, Any?>> {
        logger.info(
            "Saving generation record: strategy={}, projectId={}, score={}",
            record.strategy, record.projectId, record.scoreTotal
        )

        val entity = GenerationRecord(
            id = record.id ?: java.util.UUID.randomUUID().toString(),
            projectId = record.projectId,
            strategy = record.strategy,
            prompt = record.prompt,
            generatedCode = record.generatedCode,
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

        return ResponseEntity.ok(mapOf("success" to true, "recordId" to entity.id))
    }

    @PostMapping("/export")
    fun exportMetrics(@RequestBody request: ExportRequest): ResponseEntity<Any> {
        val result = exportService.export(request)

        val contentType = when (request.format) {
            ExportFormat.CSV -> "text/csv"
            ExportFormat.JSON, ExportFormat.JSON_PRETTY -> "application/json"
        }

        val filename = "archassistant-metrics-${request.projectId ?: "all"}-${result.generatedAt}.${request.format.name.lowercase()}"

        val builder = ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")

        if (result.warnings.isNotEmpty()) {
            builder.header("X-ArchAssistant-Warning", result.warnings.joinToString(" | "))
        }

        return builder.body(result.content)
    }

    @GetMapping("/compare")
    fun compareStrategiesDetailed(
        @RequestParam(required = false) projectId: String?
    ): ResponseEntity<Any> {
        val result = comparisonService.compare(projectId)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{projectId}/history")
    fun getGenerationHistory(
        @PathVariable projectId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) strategy: String?,
        @RequestParam(required = false) fromDate: LocalDateTime?,
        @RequestParam(required = false) toDate: LocalDateTime?
    ): ResponseEntity<Map<String, Any>> {
        val records = when {
            strategy != null && fromDate != null && toDate != null ->
                metricsRepository.findByProjectIdAndStrategyAndCreatedAtBetween(
                    projectId,
                    StrategyType.valueOf(strategy.uppercase()),
                    fromDate,
                    toDate
                )

            strategy != null -> {
                val strategyType = runCatching { StrategyType.valueOf(strategy.uppercase()) }.getOrNull()
                    ?: return ResponseEntity.badRequest().body(mapOf("error" to "Unknown strategy: $strategy"))
                metricsRepository.findByProjectIdAndStrategy(projectId, strategyType)
            }

            fromDate != null && toDate != null ->
                metricsRepository.findByProjectIdAndCreatedAtBetween(projectId, fromDate, toDate)

            else ->
                metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
        }

        val paginated = records.drop(page * size).take(size)
        val total = records.size
        val totalPages = (total + size - 1) / size

        return ResponseEntity.ok(
            mapOf(
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
                        "totalTimeMs" to (record.generationTimeMs + record.validationTimeMs),
                        "violationsCount" to record.violationsCount,
                        "createdAt" to record.createdAt
                    )
                }
            )
        )
    }
}