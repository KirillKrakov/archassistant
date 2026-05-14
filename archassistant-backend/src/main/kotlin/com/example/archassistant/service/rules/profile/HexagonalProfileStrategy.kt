package com.example.archassistant.service.rules.profile

import com.example.archassistant.model.Severity
import com.example.archassistant.model.context.ProjectProfile
import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.model.rules.ConstraintType
import com.example.archassistant.service.rules.ProfileRuleStrategy
import com.example.archassistant.service.rules.RuleGenerationContext
import com.example.archassistant.service.rules.profile.common.*
import com.example.archassistant.service.rules.profile.common.addAnnotationNoRule
import com.example.archassistant.service.rules.profile.common.addCycleRule
import com.example.archassistant.service.rules.profile.common.addInterfaceRule
import com.example.archassistant.service.rules.profile.common.addPackageDependencyRule
import com.example.archassistant.util.PackagePatternBuilder
import org.springframework.stereotype.Service

@Service
class HexagonalProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.HEXAGONAL

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        val domainPattern = PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("domain", "core")
        ) ?: return emptyList()

        val applicationPattern = PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("application", "usecase", "usecases", "interactor")
        )

        val portPattern = PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("port", "ports", "spi", "contract", "gateway", "gateways")
        )

        val adapterPattern = PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("adapter", "adapters", "impl", "implementation", "persistence", "web", "boundary", "boundaries")
        )

        val rules = mutableListOf<ArchitecturalRule>()

        if (domainPattern.isNotBlank()) {
            rules.addAnnotationNoRule(
                context = context,
                prefix = "hexagonal",
                scopePackage = domainPattern,
                annotation = "org.springframework.stereotype.Service",
                name = "Domain should not use @Service",
                description = "Domain layer should not depend on Spring service annotations",
                severity = Severity.WARNING,
                weight = 0.8
            )
        }

        if (applicationPattern != null) {
            rules.addAnnotationNoRule(
                context = context,
                prefix = "hexagonal",
                scopePackage = applicationPattern,
                annotation = "org.springframework.web.bind.annotation.RestController",
                name = "Application should not use @RestController",
                description = "Application layer should not depend on web annotations",
                severity = Severity.WARNING,
                weight = 0.8
            )
        }

        if (adapterPattern != null) {
            rules.addPackageDependencyRule(
                context = context,
                prefix = "hexagonal",
                fromPackage = domainPattern,
                toPackage = adapterPattern,
                name = "Domain should not depend on adapters",
                description = "Domain layer must stay independent from adapters",
                severity = Severity.CRITICAL,
                weight = 2.0
            )

            if (applicationPattern != null) {
                rules.addPackageDependencyRule(
                    context = context,
                    prefix = "hexagonal",
                    fromPackage = applicationPattern,
                    toPackage = adapterPattern,
                    name = "Application should not depend on adapters",
                    description = "Application layer must not depend on adapter implementations",
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }
        }

        if (portPattern != null) {
            if (applicationPattern != null) {
                rules.addPackageDependencyRule(
                    context = context,
                    prefix = "hexagonal",
                    fromPackage = applicationPattern,
                    toPackage = portPattern,
                    name = "Application should depend on ports",
                    description = "Application layer should communicate through ports",
                    constraint = ConstraintType.MUST_DEPEND,
                    severity = Severity.WARNING,
                    weight = 1.0
                )
            }

            rules.addPackageDependencyRule(
                context = context,
                prefix = "hexagonal",
                fromPackage = domainPattern,
                toPackage = portPattern,
                name = "Domain should not depend on ports",
                description = "Domain layer should not depend on port definitions",
                severity = Severity.ERROR,
                weight = 1.5
            )
        }

        if (adapterPattern != null) {
            rules.addPackageDependencyRule(
                context = context,
                prefix = "hexagonal",
                fromPackage = adapterPattern,
                toPackage = domainPattern,
                name = "Adapters should not depend on domain",
                description = "Adapters should not own domain logic",
                severity = Severity.ERROR,
                weight = 1.5
            )
        }

        val basePackage = context.basePackage
        if (basePackage.isNotBlank()) {
            rules.addCycleRule(
                context = context,
                prefix = "hexagonal",
                scopePattern = "$basePackage.(*)..*",
                name = "Hexagonal modules should be free of cycles",
                description = "Hexagonal architecture should avoid cyclic dependencies between package slices",
                severity = Severity.CRITICAL,
                weight = 2.0
            )
        }

        if (portPattern != null && applicationPattern != null) {
            rules.addInterfaceRule(
                context = context,
                prefix = "hexagonal",
                fromPackage = applicationPattern,
                toPackage = portPattern,
                name = "Application should implement port interfaces",
                description = "Application layer should implement the input port contracts",
                constraint = ConstraintType.SHOULD_IMPLEMENT,
                severity = Severity.INFO,
                weight = 1.0
            )
        }

        if (portPattern != null && adapterPattern != null) {
            rules.addInterfaceRule(
                context = context,
                prefix = "hexagonal",
                fromPackage = adapterPattern,
                toPackage = portPattern,
                name = "Adapters should implement port interfaces",
                description = "Adapters should implement the relevant port contracts",
                constraint = ConstraintType.SHOULD_IMPLEMENT,
                severity = Severity.INFO,
                weight = 1.0
            )
        }

        if (adapterPattern != null) {
            rules.addInheritanceRule(
                context = context,
                prefix = "hexagonal",
                fromPackage = adapterPattern,
                toPackage = domainPattern,
                name = "Adapters should not extend domain types",
                description = "Adapters should not inherit from domain classes",
                constraint = ConstraintType.SHOULD_NOT_EXTEND,
                severity = Severity.ERROR,
                weight = 1.2
            )
        }

        return rules.distinctBy { it.id }
    }
}