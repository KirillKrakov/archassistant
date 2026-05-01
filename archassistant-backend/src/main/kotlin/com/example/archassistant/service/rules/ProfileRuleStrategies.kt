package com.example.archassistant.service.rules

import com.example.archassistant.model.*
import com.example.archassistant.util.PackagePatternBuilder
import org.springframework.stereotype.Service

@Service
class SpringLayeredProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.SPRING_LAYERED

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        if (!context.index.isSpringLike()) return emptyList()

        val scope = if (context.basePackage.isBlank()) "..*" else "${context.basePackage}..*"
        val rules = mutableListOf<ArchitecturalRule>()

        if (context.index.hasAnyClassType(ClassType.CONTROLLER, ClassType.SERVICE)) {
            rules += RuleFactory.classDependency(
                context, "spring_layered", scope, "all",
                "Controllers should depend on services",
                "Controllers should call services rather than contain business logic",
                ClassType.CONTROLLER, ClassType.SERVICE,
                ConstraintType.MUST_DEPEND, Severity.INFO, 1.0
            )
        }

        if (context.index.hasAnyClassType(ClassType.CONTROLLER, ClassType.REPOSITORY)) {
            rules += RuleFactory.classDependency(
                context, "spring_layered", scope, "all",
                "Controllers should not depend on repositories",
                "Controllers should not access repositories directly",
                ClassType.CONTROLLER, ClassType.REPOSITORY,
                ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
            )
        }

        if (context.index.hasAnyClassType(ClassType.SERVICE, ClassType.CONTROLLER)) {
            rules += RuleFactory.classDependency(
                context, "spring_layered", scope, "all",
                "Services should not depend on controllers",
                "Service layer should remain independent from presentation",
                ClassType.SERVICE, ClassType.CONTROLLER,
                ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
            )
        }

        if (context.index.hasAnyClassType(ClassType.REPOSITORY, ClassType.SERVICE)) {
            rules += RuleFactory.classDependency(
                context, "spring_layered", scope, "all",
                "Repositories should not depend on services",
                "Data access layer should not depend on business logic",
                ClassType.REPOSITORY, ClassType.SERVICE,
                ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
            )
        }

        if (context.index.hasAnyClassType(ClassType.CONTROLLER)) {
            rules += RuleFactory.classNaming(
                context, "spring_layered", scope, "all",
                ClassType.CONTROLLER, "Controller",
                "Controllers should have 'Controller' suffix",
                "Controllers should use the Controller suffix",
                Severity.INFO, 0.5
            )
        }

        if (context.index.hasAnyClassType(ClassType.SERVICE)) {
            rules += RuleFactory.classNaming(
                context, "spring_layered", scope, "all",
                ClassType.SERVICE, "Service",
                "Services should have 'Service' suffix",
                "Services should use the Service suffix",
                Severity.INFO, 0.5
            )
        }

        if (context.index.hasAnyClassType(ClassType.REPOSITORY)) {
            rules += RuleFactory.classNaming(
                context, "spring_layered", scope, "all",
                ClassType.REPOSITORY, "Repository",
                "Repositories should have 'Repository' suffix",
                "Repositories should use the Repository suffix",
                Severity.INFO, 0.5
            )
        }

        return rules
    }
}

@Service
class SpringFeaturedProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.SPRING_FEATURED

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        val roots = context.index.springFeatureRoots()
        if (roots.isEmpty()) return emptyList()

        val rules = mutableListOf<ArchitecturalRule>()

        roots.forEach { root ->
            val scope = context.index.scopePattern(root)
            val label = root
            val hasController = context.index.hasAnyClassTypeInRoot(root, ClassType.CONTROLLER)
            val hasService = context.index.hasAnyClassTypeInRoot(root, ClassType.SERVICE)
            val hasRepository = context.index.hasAnyClassTypeInRoot(root, ClassType.REPOSITORY)

            if (hasController && hasService) {
                rules += RuleFactory.classDependency(
                    context, "spring_featured", scope, label,
                    "[$root] Controllers should depend on services",
                    "Controller classes in $root should call services within the same feature",
                    ClassType.CONTROLLER, ClassType.SERVICE,
                    ConstraintType.MUST_DEPEND, Severity.INFO, 1.0
                )
            }

            if (hasController && hasRepository) {
                rules += RuleFactory.classDependency(
                    context, "spring_featured", scope, label,
                    "[$root] Controllers should not depend on repositories",
                    "Controller classes in $root should not access repositories directly",
                    ClassType.CONTROLLER, ClassType.REPOSITORY,
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }

            if (hasService && hasController) {
                rules += RuleFactory.classDependency(
                    context, "spring_featured", scope, label,
                    "[$root] Services should not depend on controllers",
                    "Service layer in $root should remain independent from presentation",
                    ClassType.SERVICE, ClassType.CONTROLLER,
                    ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
                )
            }

            if (hasRepository && hasService) {
                rules += RuleFactory.classDependency(
                    context, "spring_featured", scope, label,
                    "[$root] Repositories should not depend on services",
                    "Repository layer in $root should stay below the service layer",
                    ClassType.REPOSITORY, ClassType.SERVICE,
                    ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
                )
            }

            if (hasController) {
                rules += RuleFactory.classNaming(
                    context, "spring_featured", scope, label,
                    ClassType.CONTROLLER, "Controller",
                    "[$root] Controllers should have 'Controller' suffix",
                    "Controllers in $root should use the Controller suffix",
                    Severity.INFO, 0.5
                )
            }

            if (hasService) {
                rules += RuleFactory.classNaming(
                    context, "spring_featured", scope, label,
                    ClassType.SERVICE, "Service",
                    "[$root] Services should have 'Service' suffix",
                    "Services in $root should use the Service suffix",
                    Severity.INFO, 0.5
                )
            }

            if (hasRepository) {
                rules += RuleFactory.classNaming(
                    context, "spring_featured", scope, label,
                    ClassType.REPOSITORY, "Repository",
                    "[$root] Repositories should have 'Repository' suffix",
                    "Repositories in $root should use the Repository suffix",
                    Severity.INFO, 0.5
                )
            }
        }

        return rules
    }
}

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

        fun addDependency(
            from: String,
            to: String,
            name: String,
            description: String,
            severity: Severity,
            weight: Double
        ) {
            if (from == to) return
            rules += RuleFactory.packageDependency(
                context = context,
                prefix = "clean",
                fromPackage = from,
                toPackage = to,
                name = name,
                description = description,
                constraint = ConstraintType.NO_DEPENDENCY,
                severity = severity,
                weight = weight
            )
        }

        if (infrastructurePattern != null) {
            addDependency(
                from = domainPattern,
                to = infrastructurePattern,
                name = "Domain should not depend on infrastructure",
                description = "Domain layer must remain isolated from infrastructure",
                severity = Severity.CRITICAL,
                weight = 2.0
            )
        }

        if (applicationPattern != null) {
            addDependency(
                from = domainPattern,
                to = applicationPattern,
                name = "Domain should not depend on application",
                description = "Domain layer should not depend on application layer",
                severity = Severity.ERROR,
                weight = 1.5
            )

            if (infrastructurePattern != null) {
                addDependency(
                    from = applicationPattern,
                    to = infrastructurePattern,
                    name = "Application should not depend on infrastructure",
                    description = "Application layer must not depend on infrastructure",
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }
        }

        if (interfacePattern != null) {
            addDependency(
                from = domainPattern,
                to = interfacePattern,
                name = "Domain should not depend on interface",
                description = "Domain layer must not depend on presentation/interface layer",
                severity = Severity.ERROR,
                weight = 1.5
            )

            if (applicationPattern != null) {
                addDependency(
                    from = applicationPattern,
                    to = interfacePattern,
                    name = "Application should not depend on interface",
                    description = "Application layer should not depend on interface layer",
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }
        }

        return rules.distinctBy { it.id }
    }
}

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

        fun addDependency(
            from: String,
            to: String,
            name: String,
            description: String,
            constraint: ConstraintType,
            severity: Severity,
            weight: Double
        ) {
            if (from == to) return
            rules += RuleFactory.packageDependency(
                context = context,
                prefix = "hexagonal",
                fromPackage = from,
                toPackage = to,
                name = name,
                description = description,
                constraint = constraint,
                severity = severity,
                weight = weight
            )
        }

        if (adapterPattern != null) {
            addDependency(
                from = domainPattern,
                to = adapterPattern,
                name = "Domain should not depend on adapters",
                description = "Domain layer must stay independent from adapters",
                constraint = ConstraintType.NO_DEPENDENCY,
                severity = Severity.CRITICAL,
                weight = 2.0
            )

            if (applicationPattern != null) {
                addDependency(
                    from = applicationPattern,
                    to = adapterPattern,
                    name = "Application should not depend on adapters",
                    description = "Application layer must not depend on adapter implementations",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }
        }

        if (portPattern != null) {
            if (applicationPattern != null) {
                addDependency(
                    from = applicationPattern,
                    to = portPattern,
                    name = "Application should depend on ports",
                    description = "Application layer should communicate through ports",
                    constraint = ConstraintType.MUST_DEPEND,
                    severity = Severity.WARNING,
                    weight = 1.0
                )
            }

            addDependency(
                from = domainPattern,
                to = portPattern,
                name = "Domain should not depend on ports",
                description = "Domain layer should not depend on port definitions",
                constraint = ConstraintType.NO_DEPENDENCY,
                severity = Severity.ERROR,
                weight = 1.5
            )
        }

        if (adapterPattern != null) {
            addDependency(
                from = adapterPattern,
                to = domainPattern,
                name = "Adapters should not depend on domain",
                description = "Adapters should not own domain logic",
                constraint = ConstraintType.NO_DEPENDENCY,
                severity = Severity.ERROR,
                weight = 1.5
            )
        }

        return rules.distinctBy { it.id }
    }
}

@Service
class MvvmProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.MVVM

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        val viewModels = context.index.scopePatternsForRoots("viewmodel", "vm")
        val views = context.index.scopePatternsForRoots("view", "ui", "screen", "fragment", "activity", "compose")

        if (viewModels.isEmpty() && views.isEmpty()) return emptyList()

        val rules = mutableListOf<ArchitecturalRule>()

        viewModels.forEach { vm ->
            views.forEach { view ->
                rules += RuleFactory.packageDependency(
                    context, "mvvm", vm, view,
                    "ViewModels should not depend on Views",
                    "ViewModel must not depend on UI components",
                    ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
                )
            }
            rules += RuleFactory.packageNaming(
                context, "mvvm", vm, "ViewModel",
                "ViewModel packages should end with ViewModel",
                "ViewModel packages should keep ViewModel naming",
                Severity.INFO, 0.5
            )
        }

        views.forEach { view ->
            rules += RuleFactory.packageNaming(
                context, "mvvm", view, "View",
                "View packages should keep View naming",
                "View packages should keep View naming",
                Severity.INFO, 0.5
            )
        }

        return rules
    }
}

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
                rules += RuleFactory.packageDependency(
                    context, "modular", fromScope, toScope,
                    "Feature modules should not depend on each other",
                    "Feature module '$fromRoot' should not depend directly on '$toRoot'",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }
        }

        val commonPackages = context.index.packagesContaining("common", "shared")
        val apiPackages = context.index.packagesContaining("api")
        val implPackages = context.index.packagesContaining("impl", "implementation")

        commonPackages.forEach { common ->
            featureRoots.forEach { root ->
                rules += RuleFactory.packageDependency(
                    context, "modular", common, context.index.scopePattern(root),
                    "Common should not depend on features",
                    "Shared/common code should stay independent from features",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }
        }

        apiPackages.forEach { api ->
            implPackages.forEach { impl ->
                rules += RuleFactory.packageDependency(
                    context, "modular", api, impl,
                    "API should not depend on implementation",
                    "Public API should not depend on impl packages",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }
            rules += RuleFactory.packageNaming(
                context, "modular", api, "Api",
                "API packages should end with Api",
                "API packages should keep Api naming",
                Severity.INFO, 0.5
            )
        }

        implPackages.forEach { impl ->
            rules += RuleFactory.packageNaming(
                context, "modular", impl, "Impl",
                "Impl packages should end with Impl",
                "Impl packages should keep Impl naming",
                Severity.INFO, 0.5
            )
        }

        return rules
    }
}