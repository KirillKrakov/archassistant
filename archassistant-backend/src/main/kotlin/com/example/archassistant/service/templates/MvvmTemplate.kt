package com.example.archassistant.service.templates

import com.example.archassistant.model.*

object MvvmArchitectureRules {

    object ViewModelViewDependency : LayerDependencyTemplate(
        id = "mvvm_viewmodel_view",
        name = "ViewModels should not depend on Views",
        description = "ViewModel не должен иметь зависимостей от UI-компонентов",
        applicablePatterns = setOf(ArchitecturePattern.MVVM),
        fromLayer = LayerType.VIEWMODEL,
        toLayer = LayerType.VIEW,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.CRITICAL,
        weight = 2.0,
        priority = 100
    )

    object ModelNaming : NamingConventionTemplate(
        id = "mvvm_model_naming",
        name = "Models should represent domain entities",
        description = "Model слой должен содержать доменные сущности",
        applicablePatterns = setOf(ArchitecturePattern.MVVM),
        targetLayer = LayerType.ENTITY,
        expectedSuffix = "",
        severity = Severity.WARNING,
        weight = 0.5,
        priority = 30
    )

    fun all(): List<RuleTemplate> = listOf(
        ViewModelViewDependency,
        ModelNaming
    )
}