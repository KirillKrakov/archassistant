package com.example.archassistant.service

import com.example.archassistant.dto.ComparisonResult
import com.example.archassistant.dto.Recommendation
import com.example.archassistant.dto.StrategyComparison
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MetricsComparisonService(
    private val recordRepository: GenerationRecordRepository
) {

    private companion object {
        const val ITERATION_TARGET = 5.0
        const val TURNAROUND_TARGET_MS = 20_000.0
        const val QUALITY_THRESHOLD = 70.0
    }

    private val logger = LoggerFactory.getLogger(MetricsComparisonService::class.java)

    /**
     * Сравнение стратегий по проекту или по всем проектам
     */
    fun compare(projectId: String? = null): ComparisonResult {
        logger.info("Comparing strategies for project: {}", projectId ?: "all")

        val records = if (projectId != null) {
            recordRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
        } else {
            recordRepository.findAll()
        }

        if (records.isEmpty()) {
            return ComparisonResult(
                projectId = projectId,
                strategies = emptyMap(),
                recommendation = null
            )
        }

        // Группируем по стратегиям
        val byStrategy = records.groupBy { it.strategy }

        val strategyComparisons = byStrategy.mapValues { (strategy, strategyRecords) ->
            calculateStrategyComparison(strategy, strategyRecords)
        }

        // Формируем рекомендацию
        val recommendation = if (strategyComparisons.size >= 2) {
            generateRecommendation(strategyComparisons.values.toList())
        } else {
            null
        }

        return ComparisonResult(
            projectId = projectId,
            strategies = strategyComparisons,
            recommendation = recommendation
        )
    }

    private fun calculateStrategyComparison(
        strategy: StrategyType,
        records: List<GenerationRecord>
    ): StrategyComparison {
        val total = records.size.toLong()

        val successRate = if (total > 0) {
            records.map { effectiveSuccessScore(it) }.average()
        } else {
            0.0
        }

        val scoreTotals = records.mapNotNull { it.scoreTotal }
        val avgScore = if (scoreTotals.isNotEmpty()) scoreTotals.average() else null

        return StrategyComparison(
            strategy = strategy,
            totalGenerations = total,
            successRate = successRate,
            avgScore = avgScore,
            avgIterations = records.map { it.iterations }.average(),
            avgGenerationTimeMs = records.map { it.generationTimeMs }.average(),
            avgValidationTimeMs = records.map { it.validationTimeMs }.average(),
            avgViolations = records.map { it.violationsCount }.average()
        )
    }

    private fun effectiveSuccessScore(record: GenerationRecord): Double {
        return when {
            !record.success -> 0.0
            record.scoreTotal == null || record.scoreTotal <= QUALITY_THRESHOLD -> 0.5
            else -> 1.0
        }
    }

    private fun generateRecommendation(comparisons: List<StrategyComparison>): Recommendation {
        val scored = comparisons.map { comp ->
            val qualityScore = (comp.avgScore ?: 0.0).coerceIn(0.0, 100.0) / 100.0
            val turnaroundMs = comp.avgGenerationTimeMs + comp.avgValidationTimeMs
            val timeScore = 1.0 - (turnaroundMs / TURNAROUND_TARGET_MS).coerceIn(0.0, 1.0)
            val iterationScore = 1.0 - (comp.avgIterations / ITERATION_TARGET).coerceIn(0.0, 1.0)

            val score = (
                    qualityScore * 0.55 +
                            comp.successRate * 0.25 +
                            timeScore * 0.15 +
                            iterationScore * 0.05
                    ).coerceIn(0.0, 1.0)

            comp to score
        }

        val best = scored.maxByOrNull { it.second } ?: return Recommendation(
            bestStrategy = StrategyType.HYBRID,
            reason = "No data to compare",
            confidence = 0.0
        )

        val runnerUp = scored.sortedByDescending { it.second }.getOrNull(1)
        val confidence = if (runnerUp == null) {
            best.second
        } else {
            (0.55 + (best.second - runnerUp.second).coerceIn(0.0, 1.0) * 0.45).coerceIn(0.0, 1.0)
        }

        val reason = buildString {
            append("Best balance of effective success (${formatPercent(best.first.successRate)}%), ")
            append("avg quality score (${formatScore(best.first.avgScore)}%), ")
            append("and total turnaround time (${(best.first.avgGenerationTimeMs + best.first.avgValidationTimeMs).toInt()}ms)")
        }

        return Recommendation(
            bestStrategy = best.first.strategy,
            reason = reason,
            confidence = confidence
        )
    }

    private fun formatPercent(value: Double): String = "%.1f".format(value * 100.0)
    private fun formatScore(value: Double?): String = value?.let { "%.1f".format(it) } ?: "N/A"
}