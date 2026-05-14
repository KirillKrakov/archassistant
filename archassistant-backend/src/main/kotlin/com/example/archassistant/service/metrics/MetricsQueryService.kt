package com.example.archassistant.service.metrics

import com.example.archassistant.dto.GenerationRecordDto
import com.example.archassistant.dto.StrategyMetrics
import com.example.archassistant.dto.metrics.*
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class MetricsQueryService(
    private val metricsRepository: GenerationRecordRepository
) {

    fun compareStrategies(projectId: String?): StrategyMetricsOverviewResponse {
        val metrics = if (projectId.isNullOrBlank()) {
            metricsRepository.getAllMetrics()
        } else {
            metricsRepository.getMetricsByProject(projectId)
        }

        return StrategyMetricsOverviewResponse(
            projectId = projectId,
            metrics = metrics
        )
    }

    fun getProjectMetrics(projectId: String): ProjectMetricsResponse {
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
            avgScore = round2(avgScore),
            avgIterations = round2(avgIterations),
            avgGenerationTimeMs = round2(avgGenerationTimeMs),
            avgValidationTimeMs = round2(avgValidationTimeMs),
            avgTotalTimeMs = round2(avgTotalTimeMs),
            successRate = round2(successRate),
            successRatePercent = successRate?.let { round2(it * 100.0) },
            lastGeneration = lastGeneration?.toString(),
            recentHistory = history.map(::toProjectHistoryItem)
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

    fun saveGenerationRecord(record: GenerationRecordDto): SaveGenerationRecordResponse {
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

        return SaveGenerationRecordResponse(
            success = true,
            recordId = entity.id
        )
    }

    fun getGenerationHistory(
        projectId: String,
        page: Int,
        size: Int,
        strategy: String?,
        fromDate: LocalDateTime?,
        toDate: LocalDateTime?
    ): GenerationHistoryResponse {
        require(page >= 0 && size > 0) { "page must be >= 0 and size must be > 0" }

        val pageable = PageRequest.of(page, size)

        val pageResult = when {
            strategy != null && fromDate != null && toDate != null -> {
                val strategyType = parseStrategy(strategy)
                metricsRepository.findByProjectIdAndStrategyAndCreatedAtBetween(
                    projectId = projectId,
                    strategy = strategyType,
                    fromDate = fromDate,
                    toDate = toDate,
                    pageable = pageable
                )
            }

            strategy != null -> {
                val strategyType = parseStrategy(strategy)
                metricsRepository.findByProjectIdAndStrategy(
                    projectId = projectId,
                    strategy = strategyType,
                    pageable = pageable
                )
            }

            fromDate != null && toDate != null ->
                metricsRepository.findByProjectIdAndCreatedAtBetween(
                    projectId = projectId,
                    fromDate = fromDate,
                    toDate = toDate,
                    pageable = pageable
                )

            else ->
                metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
        }

        return GenerationHistoryResponse(
            projectId = projectId,
            page = page,
            size = size,
            totalRecords = pageResult.totalElements,
            totalPages = pageResult.totalPages,
            records = pageResult.content.map(::toHistoryRecord)
        )
    }

    private fun parseStrategy(strategy: String): StrategyType {
        return runCatching { StrategyType.valueOf(strategy.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unknown strategy: $strategy") }
    }

    private fun toProjectHistoryItem(record: GenerationRecord): ProjectMetricsHistoryItem {
        return ProjectMetricsHistoryItem(
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

    private fun toHistoryRecord(record: GenerationRecord): GenerationHistoryRecord {
        return GenerationHistoryRecord(
            id = record.id,
            strategy = record.strategy.name,
            success = record.success,
            score = record.scoreTotal,
            iterations = record.iterations,
            generationTimeMs = record.generationTimeMs,
            validationTimeMs = record.validationTimeMs,
            totalTimeMs = record.generationTimeMs + record.validationTimeMs,
            violationsCount = record.violationsCount,
            createdAt = record.createdAt
        )
    }

    private fun round2(value: Double?): Double? {
        return value?.let { String.format(java.util.Locale.US, "%.2f", it).toDouble() }
    }
}