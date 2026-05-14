package com.example.archassistant.controller

import com.example.archassistant.dto.ExportFormat
import com.example.archassistant.dto.ExportRequest
import com.example.archassistant.dto.GenerationRecordDto
import com.example.archassistant.dto.metrics.*
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.service.metrics.MetricsComparisonService
import com.example.archassistant.service.metrics.MetricsExportService
import com.example.archassistant.service.metrics.MetricsQueryService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = ["*"])
class MetricsController(
    private val metricsQueryService: MetricsQueryService,
    private val exportService: MetricsExportService,
    private val comparisonService: MetricsComparisonService
) {

    private val logger = LoggerFactory.getLogger(MetricsController::class.java)

    @GetMapping("/strategies")
    fun compareStrategies(
        @RequestParam(required = false) projectId: String?
    ): ResponseEntity<StrategyMetricsOverviewResponse> {
        return ResponseEntity.ok(metricsQueryService.compareStrategies(projectId))
    }

    @GetMapping("/{projectId}")
    fun getProjectMetrics(@PathVariable projectId: String): ResponseEntity<ProjectMetricsResponse> {
        return ResponseEntity.ok(metricsQueryService.getProjectMetrics(projectId))
    }

    @Transactional
    @DeleteMapping("/{projectId}")
    fun clearProjectMetrics(@PathVariable projectId: String): ResponseEntity<ClearMetricsResponse> {
        return ResponseEntity.ok(metricsQueryService.clearProjectMetrics(projectId))
    }

    @PostMapping("/record")
    fun saveGenerationRecord(@RequestBody record: GenerationRecordDto): ResponseEntity<SaveGenerationRecordResponse> {
        return ResponseEntity.ok(metricsQueryService.saveGenerationRecord(record))
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
    ): ResponseEntity<GenerationHistoryResponse> {
        return ResponseEntity.ok(
            metricsQueryService.getGenerationHistory(
                projectId = projectId,
                page = page,
                size = size,
                strategy = strategy,
                fromDate = fromDate,
                toDate = toDate
            )
        )
    }
}