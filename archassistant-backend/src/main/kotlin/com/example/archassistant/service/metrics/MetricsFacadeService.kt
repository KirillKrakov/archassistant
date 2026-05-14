package com.example.archassistant.service.metrics

import com.example.archassistant.dto.metrics.request.GenerationRecordRequest
import com.example.archassistant.dto.metrics.response.*
import com.example.archassistant.entity.GenerationRecord
import com.example.archassistant.model.core.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class MetricsFacadeService(
    private val metricsRepository: GenerationRecordRepository
) {
    private val logger = LoggerFactory.getLogger(MetricsFacadeService::class.java)

    fun buildStrategyMetrics(projectId: String?): StrategyMetricsMapResponse {
        val metrics = if (projectId.isNullOrBlank()) {
            metricsRepository.getAllMetrics()
        } else {
            metricsRepository.getMetricsByProject(projectId)
        }

        return StrategyMetricsMapResponse(
            strategies = metrics.associate { metric ->
                metric.strategy.name to metric
            }
        )
    }

    fun buildProjectMetrics(projectId: String): ProjectMetricsResponse {
        val fullHistory = metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
        val history = fullHistory.take(10)

        val totalGenerations = fullHistory.size.toLong()
        val successfulGenerations = fullHistory.count { it.success }.toLong()
        val failedGenerations = totalGenerations - successfulGenerations

        val avgScore = fullHistory.mapNotNull { it.scoreTotal }.takeIf { it.isNotEmpty() }?.average()
        val avgIterations = fullHistory.map { it.iterations.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val avgGenerationTimeMs = fullHistory.map { it.generationTimeMs.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val avgValidationTimeMs = fullHistory.map { it.validationTimeMs.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val avgTotalTimeMs = fullHistory.map { (it.generationTimeMs + it.validationTimeMs).toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val successRate = if (totalGenerations > 0) successfulGenerations.toDouble() / totalGenerations.toDouble() else null
        val lastGeneration = fullHistory.firstOrNull()?.createdAt

        return ProjectMetricsResponse(
            projectId = projectId,
            totalGenerations = totalGenerations,
            successfulGenerations = successfulGenerations,
            failedGenerations = failedGenerations,
            avgScore = avgScore?.round2(),
            avgIterations = avgIterations?.round2(),
            avgGenerationTimeMs = avgGenerationTimeMs?.round2(),
            avgValidationTimeMs = avgValidationTimeMs?.round2(),
            avgTotalTimeMs = avgTotalTimeMs?.round2(),
            successRate = successRate?.round2(),
            successRatePercent = successRate?.let { (it * 100.0).round2() },
            lastGeneration = lastGeneration?.toString(),
            recentHistory = history.map { record ->
                ProjectMetricsHistoryItem(
                    id = record.id,
                    strategy = record.strategy.name,
                    score = record.scoreTotal,
                    iterations = record.iterations,
                    success = record.success,
                    generationTimeMs = record.generationTimeMs,
                    validationTimeMs = record.validationTimeMs,
                    totalTimeMs = record.generationTimeMs + record.validationTimeMs,
                    violationsCount = record.violationsCount,
                    timestamp = record.createdAt.toString(),
                    prompt = record.prompt,
                    generatedCode = record.generatedCode
                )
            }
        )
    }

    @Transactional
    fun clearProjectMetrics(projectId: String): ClearMetricsResponse {
        val records = metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
        val deletedCount = records.size

        if (deletedCount > 0) {
            metricsRepository.deleteAllInBatch(records)
        }

        return ClearMetricsResponse(
            success = true,
            projectId = projectId,
            deletedCount = deletedCount
        )
    }

    fun saveManualRecord(record: GenerationRecordRequest): SaveGenerationRecordResponse {
        return try {
            val entity = GenerationRecord(
                id = record.id ?: UUID.randomUUID().toString(),
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
            SaveGenerationRecordResponse(success = true, recordId = entity.id)
        } catch (e: Exception) {
            logger.warn("Failed to save generation record: {}", e.message, e)
            SaveGenerationRecordResponse(success = false, error = e.message ?: "Failed to save generation record")
        }
    }

    fun buildGenerationHistory(
        projectId: String,
        page: Int,
        size: Int,
        strategy: String?,
        fromDate: LocalDateTime?,
        toDate: LocalDateTime?
    ): GenerationHistoryResponse {
        val pageable = PageRequest.of(page, size)

        val pageResult = when {
            strategy != null && fromDate != null && toDate != null -> {
                val strategyType = parseStrategy(strategy)
                metricsRepository.findByProjectIdAndStrategyAndCreatedAtBetween(
                    projectId,
                    strategyType,
                    fromDate,
                    toDate,
                    pageable
                )
            }

            strategy != null -> {
                val strategyType = parseStrategy(strategy)
                metricsRepository.findByProjectIdAndStrategy(projectId, strategyType, pageable)
            }

            fromDate != null && toDate != null ->
                metricsRepository.findByProjectIdAndCreatedAtBetween(projectId, fromDate, toDate, pageable)

            else ->
                metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
        }

        return GenerationHistoryResponse(
            projectId = projectId,
            page = page,
            size = size,
            totalRecords = pageResult.totalElements,
            totalPages = pageResult.totalPages,
            records = pageResult.content.map { record ->
                GenerationHistoryItem(
                    id = record.id,
                    strategy = record.strategy.name,
                    success = record.success,
                    score = record.scoreTotal,
                    iterations = record.iterations,
                    generationTimeMs = record.generationTimeMs,
                    validationTimeMs = record.validationTimeMs,
                    totalTimeMs = record.generationTimeMs + record.validationTimeMs,
                    violationsCount = record.violationsCount,
                    createdAt = record.createdAt.toString()
                )
            }
        )
    }

    private fun parseStrategy(strategy: String): StrategyType {
        return runCatching { StrategyType.valueOf(strategy.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unknown strategy: $strategy") }
    }

    private fun Double.round2(): Double = String.format(java.util.Locale.US, "%.2f", this).toDouble()
}