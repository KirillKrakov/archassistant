package com.example.archassistant.service.templates

import com.example.archassistant.model.*

object HexagonalRules {

    object DomainPortsIsolation : LayerDependencyTemplate(
        id = "hex_domain_ports",
        name = "Domain should not depend on adapters",
        description = "Домен не должен зависеть от адаптеров",
        applicablePatterns = setOf(ArchitecturePattern.HEXAGONAL),
        fromLayer = LayerType.DOMAIN,
        toLayer = LayerType.ADAPTER,
        constraint = ConstraintType.NO_DEPENDENCY,
        severity = Severity.CRITICAL,
        weight = 2.0,
        priority = 100
    )

    object PortsAbstraction : LayerDependencyTemplate(
        id = "hex_ports_abstraction",
        name = "Application should depend on ports, not implementations",
        description = "Application слой должен зависеть от интерфейсов портов",
        applicablePatterns = setOf(ArchitecturePattern.HEXAGONAL),
        fromLayer = LayerType.APPLICATION,
        toLayer = LayerType.PORT,
        constraint = ConstraintType.MUST_DEPEND,
        severity = Severity.WARNING,
        weight = 1.0,
        priority = 80
    )

    fun all(): List<RuleTemplate> = listOf(
        DomainPortsIsolation,
        PortsAbstraction
    )
}