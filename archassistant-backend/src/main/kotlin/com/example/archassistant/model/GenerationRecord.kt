package com.example.archassistant.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Запись о генерации кода для сбора метрик
 */
@Entity
@Table(name = "generation_records")
data class GenerationRecord(
    @Id
    val id: String = java.util.UUID.randomUUID().toString(),

    @Column(nullable = false)
    val projectId: String = "",

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val strategy: StrategyType = StrategyType.HYBRID,

    @Column(columnDefinition = "TEXT")
    val prompt: String? = null,

    @Column(columnDefinition = "TEXT")
    val generatedCode: String? = null,

    @Column(nullable = false)
    val success: Boolean = false,

    @Column
    val scoreTotal: Double? = null,

    @Column
    val scoreRulesPass: Double? = null,

    @Column
    val scorePatternMatch: Double? = null,

    @Column
    val scoreDependencyCorrect: Double? = null,

    @Column
    val iterations: Int = 1,

    @Column
    val generationTimeMs: Long = 0,

    @Column
    val validationTimeMs: Long = 0,

    @Column
    val violationsCount: Int = 0,

    @Column(columnDefinition = "TEXT")
    val violationsJson: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)