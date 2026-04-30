package com.example.archassistant.service.templates

import com.example.archassistant.model.*

/**
 * Набор правил для традиционной слоистой архитектуры (Spring Boot style)
 */
object LayeredArchitectureRules {

    /**
     * Правило: Сервисы не должны зависеть от контроллеров
     */
    object ServiceControllerDependency : LayerDependencyTemplate(
        id = "layered_service_controller",
        name = "Services should not depend on controllers",
        description = "Сервисный слой не должен иметь зависимостей от presentation слоя",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED),
        fromLayer = LayerType.SERVICE,
        toLayer = LayerType.CONTROLLER,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.CRITICAL,
        weight = 2.0,
        priority = 100
    )

    /**
     * Правило: Repository не должны зависеть от сервисов
     */
    object RepositoryServiceDependency : LayerDependencyTemplate(
        id = "layered_repository_service",
        name = "Repositories should not depend on services",
        description = "Data access слой не должен зависеть от бизнес-логики",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED),
        fromLayer = LayerType.REPOSITORY,
        toLayer = LayerType.SERVICE,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.CRITICAL,
        weight = 2.0,
        priority = 100
    )

    /**
     * Правило: Контроллеры не должны зависеть от Repository напрямую
     */
    object ControllerRepositoryDependency : LayerDependencyTemplate(
        id = "layered_controller_repository",
        name = "Controllers should not depend directly on repositories",
        description = "Presentation слой должен обращаться к бизнес-логике через сервисы",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED),
        fromLayer = LayerType.CONTROLLER,
        toLayer = LayerType.REPOSITORY,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.ERROR,
        weight = 1.5,
        priority = 90
    )

    /**
     * Правило: Сервисы должны иметь суффикс Service
     */
    object ServiceNaming : NamingConventionTemplate(
        id = "layered_service_naming",
        name = "Services should have 'Service' suffix",
        description = "Все классы сервисного слоя должны иметь суффикс Service",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED),
        targetLayer = LayerType.SERVICE,
        expectedSuffix = "Service",
        severity = Severity.INFO,
        weight = 0.5,
        priority = 40
    )

    /**
     * Правило: Repository должны иметь суффикс Repository
     */
    object RepositoryNaming : NamingConventionTemplate(
        id = "layered_repository_naming",
        name = "Repositories should have 'Repository' suffix",
        description = "Все классы data access слоя должны иметь суффикс Repository",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED),
        targetLayer = LayerType.REPOSITORY,
        expectedSuffix = "Repository",
        severity = Severity.INFO,
        weight = 0.5,
        priority = 40
    )

    fun all(): List<RuleTemplate> = listOf(
        ServiceControllerDependency,
        RepositoryServiceDependency,
        ControllerRepositoryDependency,
        ServiceNaming,
        RepositoryNaming
    )
}