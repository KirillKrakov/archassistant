package com.example.archassistant.service.rules.profile

import com.example.archassistant.model.*
import com.example.archassistant.service.rules.ProfileRuleStrategy
import com.example.archassistant.service.rules.RuleGenerationContext
import com.example.archassistant.service.rules.profile.common.addCycleRule
import com.example.archassistant.service.rules.profile.common.addModifierRule
import com.example.archassistant.service.rules.profile.common.addPackageDependencyRule
import com.example.archassistant.service.rules.profile.common.addPackageNamingRule
import org.springframework.stereotype.Service

@Service
class ModularProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.MODULAR

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        val featureRoots = context.index.featureRoots
        if (featureRoots.size < 2) return emptyList()

        val rules = mutableListOf<ArchitecturalRule>()

        featureRoots.forEach { fromRoot ->
            val fromScope = context.index.scopePattern(fromRoot)
            featureRoots.filter { it != fromRoot }.forEach { toRoot ->
                val toScope = context.index.scopePattern(toRoot)
                rules.addPackageDependencyRule(
                    context = context,
                    prefix = "modular",
                    fromPackage = fromScope,
                    toPackage = toScope,
                    name = "Feature modules should not depend on each other",
                    description = "Feature module '$fromRoot' should not depend directly on '$toRoot'",
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }
        }

        val commonPackages = context.index.packagesContaining("common", "shared")
        val apiPackages = context.index.packagesContaining("api")
        val implPackages = context.index.packagesContaining("impl", "implementation")

        commonPackages.forEach { common ->
            featureRoots.forEach { root ->
                rules.addPackageDependencyRule(
                    context = context,
                    prefix = "modular",
                    fromPackage = common,
                    toPackage = context.index.scopePattern(root),
                    name = "Common should not depend on features",
                    description = "Shared/common code should stay independent from features",
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }
        }

        apiPackages.forEach { api ->
            implPackages.forEach { impl ->
                rules.addPackageDependencyRule(
                    context = context,
                    prefix = "modular",
                    fromPackage = api,
                    toPackage = impl,
                    name = "API should not depend on implementation",
                    description = "Public API should not depend on impl packages",
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }

            rules.addPackageNamingRule(
                context = context,
                prefix = "modular",
                scopePackage = api,
                suffix = "Api",
                name = "API packages should end with Api",
                description = "API packages should keep Api naming",
                severity = Severity.INFO,
                weight = 0.5
            )

            rules.addModifierRule(
                context = context,
                prefix = "modular",
                scopePackage = api,
                name = "API classes should be public",
                description = "API classes should be public",
                constraint = ConstraintType.SHOULD_BE_PUBLIC,
                severity = Severity.INFO,
                weight = 0.4,
                scopeLabel = api
            )
        }

        implPackages.forEach { impl ->
            rules.addPackageNamingRule(
                context = context,
                prefix = "modular",
                scopePackage = impl,
                suffix = "Impl",
                name = "Impl packages should end with Impl",
                description = "Impl packages should keep Impl naming",
                severity = Severity.INFO,
                weight = 0.5
            )

            rules.addModifierRule(
                context = context,
                prefix = "modular",
                scopePackage = impl,
                name = "Impl classes should be final",
                description = "Implementation classes should be final when possible",
                constraint = ConstraintType.SHOULD_BE_FINAL,
                severity = Severity.WARNING,
                weight = 0.6,
                scopeLabel = impl
            )
        }

        val basePackage = context.basePackage
        if (basePackage.isNotBlank()) {
            rules.addCycleRule(
                context = context,
                prefix = "modular",
                scopePattern = "$basePackage.(*)..*",
                name = "Feature modules should be free of cycles",
                description = "Feature modules should not form cyclic dependencies",
                severity = Severity.CRITICAL,
                weight = 2.0
            )
        }

        return rules.distinctBy { it.id }
    }
}