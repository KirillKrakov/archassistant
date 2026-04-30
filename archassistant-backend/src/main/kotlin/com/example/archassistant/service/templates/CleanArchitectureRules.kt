package com.example.archassistant.service.templates

import com.example.archassistant.model.*

/**
 * Набор правил для Clean Architecture / Hexagonal
 */
object CleanArchitectureRules {

    /**
     * Правило: Domain слой не должен зависеть от Infrastructure
     */
    object DomainIsolation : LayerDependencyTemplate(
        id = "clean_domain_isolation",
        name = "Domain layer should not depend on infrastructure",
        description = "Доменный слой должен быть изолирован от внешних зависимостей",
        applicablePatterns = setOf(ArchitecturePattern.CLEAN_ARCHITECTURE, ArchitecturePattern.HEXAGONAL),
        fromLayer = LayerType.DOMAIN,
        toLayer = LayerType.INFRASTRUCTURE,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.CRITICAL,
        weight = 2.0,
        priority = 100
    )

    /**
     * Правило: Application слой не должен зависеть от Infrastructure напрямую
     */
    object ApplicationIsolation : LayerDependencyTemplate(
        id = "clean_application_isolation",
        name = "Application layer should not directly depend on infrastructure",
        description = "Application слой должен зависеть от портов, а не от реализаций",
        applicablePatterns = setOf(ArchitecturePattern.CLEAN_ARCHITECTURE, ArchitecturePattern.HEXAGONAL),
        fromLayer = LayerType.APPLICATION,
        toLayer = LayerType.INFRASTRUCTURE,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.ERROR,
        weight = 1.5
    )

    fun all(): List<RuleTemplate> = listOf(
        DomainIsolation,
        ApplicationIsolation
    )
}