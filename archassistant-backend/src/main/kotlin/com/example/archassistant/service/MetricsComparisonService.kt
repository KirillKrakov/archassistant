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
        val successCount = records.count { it.success }.toLong()
        val successRate = if (total > 0) successCount.toDouble() / total else 0.0

        val scoreTotals = records.mapNotNull { it.scoreTotal }
        val avgScore = if (scoreTotals.isNotEmpty()) scoreTotals.average() else null

        val avgIterations = records.map { it.iterations }.average()
        val avgGenTime = records.map { it.generationTimeMs }.average()
        val avgValTime = records.map { it.validationTimeMs }.average()
        val avgViolations = records.map { it.violationsCount }.average()

        return StrategyComparison(
            strategy = strategy,
            totalGenerations = total,
            successRate = successRate,
            avgScore = avgScore,
            avgIterations = avgIterations,
            avgGenerationTimeMs = avgGenTime,
            avgValidationTimeMs = avgValTime,
            avgViolations = avgViolations
        )
    }

    private fun generateRecommendation(comparisons: List<StrategyComparison>): Recommendation {
        // Простая эвристика: лучшая стратегия = высокая успешность + высокая оценка + низкое время
        val scored = comparisons.map { comp ->
            val score = comp.successRate * 0.4 +
                    (comp.avgScore?.let { it / 100.0 } ?: 0.0) * 0.3 +
                    (1.0 - (comp.avgGenerationTimeMs / 5000.0).coerceIn(0.0, 1.0)) * 0.2 +
                    (1.0 - (comp.avgIterations / 5.0).coerceIn(0.0, 1.0)) * 0.1
            comp to score
        }

        val best = scored.maxByOrNull { it.second } ?: return Recommendation(
            bestStrategy = StrategyType.HYBRID,
            reason = "No data to compare",
            confidence = 0.0
        )

        val reason = buildString {
            append("Best balance of success rate (${best.first.successRate * 100}%), ")
            append("quality score (${best.first.avgScore?.toInt()}%), ")
            append("and generation time (${best.first.avgGenerationTimeMs.toInt()}ms)")
        }

        return Recommendation(
            bestStrategy = best.first.strategy,
            reason = reason,
            confidence = best.second
        )
    }
}