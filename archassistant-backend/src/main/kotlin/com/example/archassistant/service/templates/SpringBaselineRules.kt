package com.example.archassistant.service.templates

import com.example.archassistant.model.*

object SpringBaselineRules {

    object ControllerShouldDependOnService : ClassDependencyTemplate(
        id = "spring_controller_should_depend_on_service",
        name = "Controllers should depend on services",
        description = "Контроллеры должны обращаться к сервисам, а не содержать бизнес-логику",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED, ArchitecturePattern.UNKNOWN),
        fromLayer = LayerType.CONTROLLER,
        toLayer = LayerType.SERVICE,
        constraint = ConstraintType.MUST_DEPEND,
        severity = Severity.INFO,
        weight = 1.0,
        priority = 95
    )

    object ControllerShouldNotDependOnRepository : ClassDependencyTemplate(
        id = "spring_controller_no_repository",
        name = "Controllers should not depend on repositories",
        description = "Контроллеры не должны ходить в repository напрямую",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED, ArchitecturePattern.UNKNOWN),
        fromLayer = LayerType.CONTROLLER,
        toLayer = LayerType.REPOSITORY,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.ERROR,
        weight = 1.5,
        priority = 100
    )

    object ServiceShouldNotDependOnController : ClassDependencyTemplate(
        id = "spring_service_no_controller",
        name = "Services should not depend on controllers",
        description = "Сервисный слой не должен зависеть от presentation-слоя",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED, ArchitecturePattern.UNKNOWN),
        fromLayer = LayerType.SERVICE,
        toLayer = LayerType.CONTROLLER,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.CRITICAL,
        weight = 2.0,
        priority = 100
    )

    object RepositoryShouldNotDependOnService : ClassDependencyTemplate(
        id = "spring_repository_no_service",
        name = "Repositories should not depend on services",
        description = "Data access слой не должен зависеть от бизнес-логики",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED, ArchitecturePattern.UNKNOWN),
        fromLayer = LayerType.REPOSITORY,
        toLayer = LayerType.SERVICE,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.CRITICAL,
        weight = 2.0,
        priority = 100
    )

    object ControllerNaming : ClassNamingConventionTemplate(
        id = "spring_controller_naming",
        name = "Controllers should have 'Controller' suffix",
        description = "Контроллеры должны иметь суффикс Controller",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED, ArchitecturePattern.UNKNOWN),
        targetLayer = LayerType.CONTROLLER,
        expectedSuffix = "Controller",
        severity = Severity.INFO,
        weight = 0.5,
        priority = 40
    )

    object ServiceNaming : ClassNamingConventionTemplate(
        id = "spring_service_naming",
        name = "Services should have 'Service' suffix",
        description = "Сервисы должны иметь суффикс Service",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED, ArchitecturePattern.UNKNOWN),
        targetLayer = LayerType.SERVICE,
        expectedSuffix = "Service",
        severity = Severity.INFO,
        weight = 0.5,
        priority = 40
    )

    object RepositoryNaming : ClassNamingConventionTemplate(
        id = "spring_repository_naming",
        name = "Repositories should have 'Repository' suffix",
        description = "Репозитории должны иметь суффикс Repository",
        applicablePatterns = setOf(ArchitecturePattern.LAYERED, ArchitecturePattern.UNKNOWN),
        targetLayer = LayerType.REPOSITORY,
        expectedSuffix = "Repository",
        severity = Severity.INFO,
        weight = 0.5,
        priority = 40
    )

    fun all(): List<RuleTemplate> = listOf(
        ControllerShouldDependOnService,
        ControllerShouldNotDependOnRepository,
        ServiceShouldNotDependOnController,
        RepositoryShouldNotDependOnService,
        ControllerNaming,
        ServiceNaming,
        RepositoryNaming
    )
}