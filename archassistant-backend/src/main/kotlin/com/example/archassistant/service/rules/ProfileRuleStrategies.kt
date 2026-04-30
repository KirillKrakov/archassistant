package com.example.archassistant.service.rules

import com.example.archassistant.model.*
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
        val domainPackages = context.index.packagesContaining("domain", "core")
        val applicationPackages = context.index.packagesContaining("application")
        val infrastructurePackages = context.index.packagesContaining("infrastructure")
        val interfacePackages = context.index.packagesContaining("interface", "presentation")

        if (
            domainPackages.isEmpty() &&
            applicationPackages.isEmpty() &&
            infrastructurePackages.isEmpty() &&
            interfacePackages.isEmpty()
        ) {
            return emptyList()
        }

        val rules = mutableListOf<ArchitecturalRule>()

        domainPackages.forEach { domain ->
            infrastructurePackages.forEach { infra ->
                rules += RuleFactory.packageDependency(
                    context, "clean", domain, infra,
                    "Domain should not depend on infrastructure",
                    "Domain layer must remain isolated from infrastructure",
                    ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
                )
            }
            applicationPackages.forEach { app ->
                rules += RuleFactory.packageDependency(
                    context, "clean", domain, app,
                    "Domain should not depend on application",
                    "Domain layer should not depend on application layer",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }
            interfacePackages.forEach { iface ->
                rules += RuleFactory.packageDependency(
                    context, "clean", domain, iface,
                    "Domain should not depend on interface",
                    "Domain layer must not depend on presentation/interface layer",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }

            rules += RuleFactory.annotationNo(
                context, "clean", domain, "Controller",
                "Domain should not use controllers",
                "Domain classes should not be annotated as controllers",
                Severity.ERROR, 1.0
            )
            rules += RuleFactory.annotationNo(
                context, "clean", domain, "Service",
                "Domain should not use services",
                "Domain classes should not be annotated as services",
                Severity.ERROR, 1.0
            )
            rules += RuleFactory.annotationNo(
                context, "clean", domain, "Repository",
                "Domain should not use repositories",
                "Domain classes should not be annotated as repositories",
                Severity.ERROR, 1.0
            )
        }

        applicationPackages.forEach { app ->
            infrastructurePackages.forEach { infra ->
                rules += RuleFactory.packageDependency(
                    context, "clean", app, infra,
                    "Application should not depend on infrastructure",
                    "Application layer must not depend on infrastructure",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }
            interfacePackages.forEach { iface ->
                rules += RuleFactory.packageDependency(
                    context, "clean", app, iface,
                    "Application should not depend on interface",
                    "Application layer should not depend on interface layer",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }

            rules += RuleFactory.annotationNo(
                context, "clean", app, "Controller",
                "Application should not use controllers",
                "Application classes should not be annotated as controllers",
                Severity.ERROR, 1.0
            )
            rules += RuleFactory.annotationNo(
                context, "clean", app, "Service",
                "Application should not use services",
                "Application classes should not be annotated as services",
                Severity.ERROR, 1.0
            )
            rules += RuleFactory.annotationNo(
                context, "clean", app, "Repository",
                "Application should not use repositories",
                "Application classes should not be annotated as repositories",
                Severity.ERROR, 1.0
            )
        }

        return rules
    }
}

@Service
class HexagonalProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.HEXAGONAL

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        val domainPackages = context.index.packagesContaining("domain", "core")
        val applicationPackages = context.index.packagesContaining("application", "usecase", "interactor")
        val portPackages = context.index.packagesContaining("port", "ports", "spi", "contract", "gateway")
        val adapterPackages = context.index.packagesContaining("adapter", "adapters", "impl", "implementation")

        if (
            domainPackages.isEmpty() &&
            applicationPackages.isEmpty() &&
            portPackages.isEmpty() &&
            adapterPackages.isEmpty()
        ) {
            return emptyList()
        }

        val rules = mutableListOf<ArchitecturalRule>()

        domainPackages.forEach { domain ->
            adapterPackages.forEach { adapter ->
                rules += RuleFactory.packageDependency(
                    context, "hexagonal", domain, adapter,
                    "Domain should not depend on adapters",
                    "Domain layer must stay independent from adapters",
                    ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
                )
            }
            portPackages.forEach { port ->
                rules += RuleFactory.packageDependency(
                    context, "hexagonal", domain, port,
                    "Domain should not depend on ports",
                    "Domain layer should not depend on port definitions",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }
        }

        applicationPackages.forEach { app ->
            adapterPackages.forEach { adapter ->
                rules += RuleFactory.packageDependency(
                    context, "hexagonal", app, adapter,
                    "Application should not depend on adapters",
                    "Application layer must not depend on adapter implementations",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }
            portPackages.forEach { port ->
                rules += RuleFactory.packageDependency(
                    context, "hexagonal", app, port,
                    "Application should depend on ports",
                    "Application layer should communicate through ports",
                    ConstraintType.MUST_DEPEND, Severity.WARNING, 1.0
                )
            }

            rules += RuleFactory.annotationNo(
                context, "hexagonal", app, "Service",
                "Application should not expose services",
                "Application classes should not be annotated as services",
                Severity.ERROR, 1.0
            )
            rules += RuleFactory.annotationNo(
                context, "hexagonal", app, "Repository",
                "Application should not expose repositories",
                "Application classes should not be annotated as repositories",
                Severity.ERROR, 1.0
            )
        }

        adapterPackages.forEach { adapter ->
            domainPackages.forEach { domain ->
                rules += RuleFactory.packageDependency(
                    context, "hexagonal", adapter, domain,
                    "Adapters should not depend on domain",
                    "Adapters should not own domain logic",
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }
            rules += RuleFactory.packageNaming(
                context, "hexagonal", adapter, "Adapter",
                "Adapter packages should end with Adapter",
                "Adapter packages should keep Adapter naming",
                Severity.INFO, 0.5
            )
        }

        portPackages.forEach { port ->
            rules += RuleFactory.packageNaming(
                context, "hexagonal", port, "Port",
                "Port packages should end with Port",
                "Port packages should keep Port naming",
                Severity.INFO, 0.5
            )
        }

        return rules
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