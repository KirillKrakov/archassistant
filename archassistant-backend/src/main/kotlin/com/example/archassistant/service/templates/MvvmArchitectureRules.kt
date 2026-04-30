package com.example.archassistant.service.templates

import com.example.archassistant.model.*

object MvvmArchitectureRules {

    object ViewModelViewDependency : ClassDependencyTemplate(
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

    object ViewModelNaming : ClassNamingConventionTemplate(
        id = "mvvm_viewmodel_naming",
        name = "ViewModels should have 'ViewModel' suffix",
        description = "ViewModel-классы должны иметь суффикс ViewModel",
        applicablePatterns = setOf(ArchitecturePattern.MVVM),
        targetLayer = LayerType.VIEWMODEL,
        expectedSuffix = "ViewModel",
        severity = Severity.INFO,
        weight = 0.5,
        priority = 40
    )

    fun all(): List<RuleTemplate> = listOf(
        ViewModelViewDependency,
        ViewModelNaming
    )
}