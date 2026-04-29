package com.example.archassistant.controller

import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.repository.GenerationRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlin.math.pow
import kotlin.math.roundToInt

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = ["*"])
class MetricsController(
    private val metricsRepository: GenerationRecordRepository
) {

    private val logger = LoggerFactory.getLogger(MetricsController::class.java)

    /**
     * Получить сравнение стратегий (все проекты или конкретный)
     */
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

    /**
     * Получить статистику по проекту
     */
    @GetMapping("/{projectId}")
    fun getProjectMetrics(@PathVariable projectId: String): ResponseEntity<Map<String, Any?>> {
        val totalGenerations = metricsRepository.countByProjectId(projectId)
        val history = metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId).take(10)
        val avgScore = history.mapNotNull { it.scoreTotal }.takeIf { it.isNotEmpty() }?.average()
        val lastGeneration = history.firstOrNull()?.createdAt

        return ResponseEntity.ok(mapOf(
            "projectId" to projectId,
            "totalGenerations" to totalGenerations,
            "avgScore" to avgScore?.roundTo(2),
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

    /**
     * Сохранить запись о генерации (внутренний эндпоинт для системы)
     */
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
                violationsCount = record.violationsCount
            )

            metricsRepository.save(entity)

            ResponseEntity.ok(mapOf("success" to true, "recordId" to entity.id))
        } catch (e: Exception) {
            logger.error("Failed to save generation record: ${e.message}", e)
            val errorMap: Map<String, Any> = mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error")
            )
            ResponseEntity.internalServerError().body(errorMap)
        }
    }

    // Extension function для округления Double
    private fun Double.roundTo(decimalPlaces: Int): Double {
        val multiplier = 10.0.pow(decimalPlaces.toDouble())
        return (this * multiplier).roundToInt() / multiplier
    }
}

/**
 * DTO для сохранения записи о генерации
 */
data class GenerationRecordDto(
    val id: String? = null,
    val projectId: String,
    val strategy: com.example.archassistant.model.StrategyType,
    val success: Boolean,
    val scoreTotal: Double? = null,
    val scoreRulesPass: Double? = null,
    val scorePatternMatch: Double? = null,
    val scoreDependencyCorrect: Double? = null,
    val iterations: Int = 1,
    val generationTimeMs: Long = 0,
    val validationTimeMs: Long = 0,
    val violationsCount: Int = 0
)