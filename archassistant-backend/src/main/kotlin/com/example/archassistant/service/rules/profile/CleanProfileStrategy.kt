package com.example.archassistant.service.rules.profile

import com.example.archassistant.model.*
import com.example.archassistant.service.rules.ProfileRuleStrategy
import com.example.archassistant.service.rules.RuleGenerationContext
import com.example.archassistant.service.rules.profile.common.addAnnotationNoRule
import com.example.archassistant.service.rules.profile.common.addCycleRule
import com.example.archassistant.service.rules.profile.common.addInheritanceRule
import com.example.archassistant.service.rules.profile.common.addPackageDependencyRule
import com.example.archassistant.util.PackagePatternBuilder
import org.springframework.stereotype.Service

@Service
class CleanProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.CLEAN

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        val domainPattern = PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("domain", "core", "entity", "entities")
        ) ?: return emptyList()

        val applicationPattern = PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("application", "usecase", "usecases", "interactor")
        )

        val infrastructurePattern = PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("infrastructure", "persistence", "adapter", "gateway", "dataproviders", "repository")
        )

        val interfacePattern = PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("interface", "presentation", "web", "rest", "api", "delivery", "boundary", "boundaries")
        )

        val rules = mutableListOf<ArchitecturalRule>()

        fun addCoreAnnotationRestrictions(scopePackage: String) {
            rules.addAnnotationNoRule(
                context = context,
                prefix = "clean",
                scopePackage = scopePackage,
                annotation = "org.springframework.stereotype.Service",
                name = "Core packages should not use @Service",
                description = "Core and application packages should not depend on Spring service annotations",
                severity = Severity.WARNING,
                weight = 0.8
            )

            rules.addAnnotationNoRule(
                context = context,
                prefix = "clean",
                scopePackage = scopePackage,
                annotation = "org.springframework.stereotype.Repository",
                name = "Core packages should not use @Repository",
                description = "Core and application packages should not depend on Spring repository annotations",
                severity = Severity.WARNING,
                weight = 0.8
            )

            rules.addAnnotationNoRule(
                context = context,
                prefix = "clean",
                scopePackage = scopePackage,
                annotation = "org.springframework.web.bind.annotation.RestController",
                name = "Core packages should not use @RestController",
                description = "Core and application packages should not depend on web annotations",
                severity = Severity.WARNING,
                weight = 0.8
            )
        }

        listOfNotNull(domainPattern, applicationPattern).forEach { pkg ->
            addCoreAnnotationRestrictions(pkg)
        }

        if (infrastructurePattern != null) {
            rules.addPackageDependencyRule(
                context = context,
                prefix = "clean",
                fromPackage = domainPattern,
                toPackage = infrastructurePattern,
                name = "Domain should not depend on infrastructure",
                description = "Domain layer must remain isolated from infrastructure",
                severity = Severity.CRITICAL,
                weight = 2.0
            )
        }

        if (applicationPattern != null) {
            rules.addPackageDependencyRule(
                context = context,
                prefix = "clean",
                fromPackage = domainPattern,
                toPackage = applicationPattern,
                name = "Domain should not depend on application",
                description = "Domain layer should not depend on application layer",
                severity = Severity.ERROR,
                weight = 1.5
            )

            if (infrastructurePattern != null) {
                rules.addPackageDependencyRule(
                    context = context,
                    prefix = "clean",
                    fromPackage = applicationPattern,
                    toPackage = infrastructurePattern,
                    name = "Application should not depend on infrastructure",
                    description = "Application layer must not depend on infrastructure",
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }
        }

        if (interfacePattern != null) {
            rules.addPackageDependencyRule(
                context = context,
                prefix = "clean",
                fromPackage = domainPattern,
                toPackage = interfacePattern,
                name = "Domain should not depend on interface",
                description = "Domain layer must not depend on presentation/interface layer",
                severity = Severity.ERROR,
                weight = 1.5
            )

            if (applicationPattern != null) {
                rules.addPackageDependencyRule(
                    context = context,
                    prefix = "clean",
                    fromPackage = applicationPattern,
                    toPackage = interfacePattern,
                    name = "Application should not depend on interface",
                    description = "Application layer should not depend on interface layer",
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }
        }

        val basePackage = context.basePackage
        if (basePackage.isNotBlank()) {
            rules.addCycleRule(
                context = context,
                prefix = "clean",
                scopePattern = "$basePackage.(*)..*",
                name = "Architecture should be free of package cycles",
                description = "Clean architecture layers should not form cyclic package dependencies",
                severity = Severity.CRITICAL,
                weight = 2.0
            )
        }

        if (infrastructurePattern != null) {
            rules.addInheritanceRule(
                context = context,
                prefix = "clean",
                fromPackage = domainPattern,
                toPackage = infrastructurePattern,
                name = "Domain should not extend infrastructure types",
                description = "Domain layer should not inherit from infrastructure classes",
                constraint = ConstraintType.SHOULD_NOT_EXTEND,
                severity = Severity.CRITICAL,
                weight = 1.5
            )

            if (applicationPattern != null) {
                rules.addInheritanceRule(
                    context = context,
                    prefix = "clean",
                    fromPackage = applicationPattern,
                    toPackage = infrastructurePattern,
                    name = "Application should not extend infrastructure types",
                    description = "Application layer should not inherit from infrastructure classes",
                    constraint = ConstraintType.SHOULD_NOT_EXTEND,
                    severity = Severity.ERROR,
                    weight = 1.2
                )
            }
        }

        return rules.distinctBy { it.id }
    }
}