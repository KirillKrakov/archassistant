package com.example.archassistant.service.templates

import com.example.archassistant.model.*

object ModularRules {

    object ModuleApiIsolation : LayerDependencyTemplate(
        id = "modular_api_isolation",
        name = "Modules should depend on APIs, not implementations",
        description = "Модули должны зависеть только от публичных API других модулей",
        applicablePatterns = setOf(ArchitecturePattern.MODULAR),
        fromLayer = LayerType.IMPL,
        toLayer = LayerType.API,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.CRITICAL,
        weight = 2.0,
        priority = 100
    )

    object CommonModuleIsolation : LayerDependencyTemplate(
        id = "modular_common_isolation",
        name = "Common module should not depend on feature modules",
        description = "Общий модуль не должен зависеть от функциональных модулей",
        applicablePatterns = setOf(ArchitecturePattern.MODULAR),
        fromLayer = LayerType.OTHER, // common
        toLayer = LayerType.OTHER,   // feature
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.ERROR,
        weight = 1.5,
        priority = 90
    )

    fun all(): List<RuleTemplate> = listOf(
        ModuleApiIsolation,
        CommonModuleIsolation
    )
}