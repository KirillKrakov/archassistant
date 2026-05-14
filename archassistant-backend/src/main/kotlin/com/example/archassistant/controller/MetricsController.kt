package com.example.archassistant.controller

import com.example.archassistant.dto.metrics.request.ExportFormat
import com.example.archassistant.dto.metrics.request.ExportRequest
import com.example.archassistant.dto.metrics.request.GenerationRecordRequest
import com.example.archassistant.dto.metrics.response.*
import com.example.archassistant.service.metrics.MetricsComparisonService
import com.example.archassistant.service.metrics.MetricsExportService
import com.example.archassistant.service.metrics.MetricsFacadeService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = ["*"])
class MetricsController(
    private val metricsFacadeService: MetricsFacadeService,
    private val exportService: MetricsExportService,
    private val comparisonService: MetricsComparisonService
) {

    @GetMapping("/strategies")
    fun compareStrategies(
        @RequestParam(required = false) projectId: String?
    ): ResponseEntity<StrategyMetricsMapResponse> {
        return ResponseEntity.ok(metricsFacadeService.buildStrategyMetrics(projectId))
    }

    @GetMapping("/{projectId}")
    fun getProjectMetrics(@PathVariable projectId: String): ResponseEntity<ProjectMetricsResponse> {
        return ResponseEntity.ok(metricsFacadeService.buildProjectMetrics(projectId))
    }

    @DeleteMapping("/{projectId}")
    fun clearProjectMetrics(@PathVariable projectId: String): ResponseEntity<ClearMetricsResponse> {
        return ResponseEntity.ok(metricsFacadeService.clearProjectMetrics(projectId))
    }

    @PostMapping("/record")
    fun saveGenerationRecord(@RequestBody record: GenerationRecordRequest): ResponseEntity<SaveGenerationRecordResponse> {
        return ResponseEntity.ok(metricsFacadeService.saveManualRecord(record))
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
    ): ResponseEntity<ComparisonResult> {
        return ResponseEntity.ok(comparisonService.compare(projectId))
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
        if (page < 0 || size <= 0) {
            return ResponseEntity.badRequest().build()
        }

        return ResponseEntity.ok(
            metricsFacadeService.buildGenerationHistory(
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