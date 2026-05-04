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

        val hasController = context.index.hasAnyClassType(ClassType.CONTROLLER)
        val hasService = context.index.hasAnyClassType(ClassType.SERVICE)
        val hasRepository = context.index.hasAnyClassType(ClassType.REPOSITORY)

        if (hasController && hasService) {
            rules += RuleFactory.classDependency(
                context, "spring_layered", scope, "all",
                "Controllers should depend on services",
                "Controllers should call services rather than contain business logic",
                ClassType.CONTROLLER, ClassType.SERVICE,
                ConstraintType.MUST_DEPEND, Severity.INFO, 1.0
            )
        }

        if (hasController && hasRepository) {
            rules += RuleFactory.classDependency(
                context, "spring_layered", scope, "all",
                "Controllers should not depend on repositories",
                "Controllers should not access repositories directly",
                ClassType.CONTROLLER, ClassType.REPOSITORY,
                ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
            )
        }

        if (hasService && hasController) {
            rules += RuleFactory.classDependency(
                context, "spring_layered", scope, "all",
                "Services should not depend on controllers",
                "Service layer should remain independent from presentation",
                ClassType.SERVICE, ClassType.CONTROLLER,
                ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
            )
        }

        if (hasRepository && hasService) {
            rules += RuleFactory.classDependency(
                context, "spring_layered", scope, "all",
                "Repositories should not depend on services",
                "Data access layer should not depend on business logic",
                ClassType.REPOSITORY, ClassType.SERVICE,
                ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
            )
        }

        if (hasController) {
            rules += RuleFactory.classNaming(
                context, "spring_layered", scope, "all",
                ClassType.CONTROLLER, "Controller",
                "Controllers should have 'Controller' suffix",
                "Controllers should use the Controller suffix",
                Severity.INFO, 0.5
            )

            rules += RuleFactory.modifierCheck(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                name = "Controllers should be public",
                description = "Controller classes should be public",
                constraint = ConstraintType.SHOULD_BE_PUBLIC,
                severity = Severity.INFO,
                weight = 0.4,
                scopeLabel = "all"
            )

            if (shouldSuggestResponseEntityRules(context)) {
                rules += RuleFactory.methodSignatureCheck(
                    context = context,
                    prefix = "spring_layered",
                    scopePackage = scope,
                    name = "Controller methods should return ResponseEntity",
                    description = "Public controller methods should expose ResponseEntity-based contracts",
                    constraint = ConstraintType.RETURN_TYPE,
                    severity = Severity.WARNING,
                    weight = 0.8,
                    methodNamePattern = "*",
                    returnType = org.springframework.http.ResponseEntity::class.java.name
                )
            }
        }

        if (hasService) {
            rules += RuleFactory.classNaming(
                context, "spring_layered", scope, "all",
                ClassType.SERVICE, "Service",
                "Services should have 'Service' suffix",
                "Services should use the Service suffix",
                Severity.INFO, 0.5
            )

            rules += RuleFactory.modifierCheck(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                name = "Services should be public",
                description = "Service classes should be public",
                constraint = ConstraintType.SHOULD_BE_PUBLIC,
                severity = Severity.INFO,
                weight = 0.4,
                scopeLabel = "all"
            )

            rules += RuleFactory.exceptionCheck(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                name = "Services should not throw generic Exception",
                description = "Service layer should avoid declaring generic Exception in throws clauses",
                constraint = ConstraintType.SHOULD_NOT_THROW,
                severity = Severity.WARNING,
                weight = 0.8,
                forbiddenExceptions = listOf("java.lang.Exception")
            )
        }

        if (hasRepository) {
            rules += RuleFactory.classNaming(
                context, "spring_layered", scope, "all",
                ClassType.REPOSITORY, "Repository",
                "Repositories should have 'Repository' suffix",
                "Repositories should use the Repository suffix",
                Severity.INFO, 0.5
            )

            rules += RuleFactory.modifierCheck(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                name = "Repositories should be public",
                description = "Repository classes should be public",
                constraint = ConstraintType.SHOULD_BE_PUBLIC,
                severity = Severity.INFO,
                weight = 0.4,
                scopeLabel = "all"
            )
        }

        return rules.distinctBy { it.id }
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
            val hasController = context.index.hasAnyClassTypeInRoot(root, ClassType.CONTROLLER)
            val hasService = context.index.hasAnyClassTypeInRoot(root, ClassType.SERVICE)
            val hasRepository = context.index.hasAnyClassTypeInRoot(root, ClassType.REPOSITORY)

            if (hasController && hasService) {
                rules += RuleFactory.classDependency(
                    context, "spring_featured", scope, root,
                    "[$root] Controllers should depend on services",
                    "Controller classes in $root should call services within the same feature",
                    ClassType.CONTROLLER, ClassType.SERVICE,
                    ConstraintType.MUST_DEPEND, Severity.INFO, 1.0
                )
            }

            if (hasController && hasRepository) {
                rules += RuleFactory.classDependency(
                    context, "spring_featured", scope, root,
                    "[$root] Controllers should not depend on repositories",
                    "Controller classes in $root should not access repositories directly",
                    ClassType.CONTROLLER, ClassType.REPOSITORY,
                    ConstraintType.NO_DEPENDENCY, Severity.ERROR, 1.5
                )
            }

            if (hasService && hasController) {
                rules += RuleFactory.classDependency(
                    context, "spring_featured", scope, root,
                    "[$root] Services should not depend on controllers",
                    "Service layer in $root should remain independent from presentation",
                    ClassType.SERVICE, ClassType.CONTROLLER,
                    ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
                )
            }

            if (hasRepository && hasService) {
                rules += RuleFactory.classDependency(
                    context, "spring_featured", scope, root,
                    "[$root] Repositories should not depend on services",
                    "Repository layer in $root should stay below the service layer",
                    ClassType.REPOSITORY, ClassType.SERVICE,
                    ConstraintType.NO_DEPENDENCY, Severity.CRITICAL, 2.0
                )
            }

            if (hasController) {
                rules += RuleFactory.classNaming(
                    context, "spring_featured", scope, root,
                    ClassType.CONTROLLER, "Controller",
                    "[$root] Controllers should have 'Controller' suffix",
                    "Controllers in $root should use the Controller suffix",
                    Severity.INFO, 0.5
                )

                rules += RuleFactory.modifierCheck(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    name = "[$root] Controllers should be public",
                    description = "Controller classes in $root should be public",
                    constraint = ConstraintType.SHOULD_BE_PUBLIC,
                    severity = Severity.INFO,
                    weight = 0.4,
                    scopeLabel = root
                )

                if (shouldSuggestResponseEntityRules(context)) {
                    rules += RuleFactory.methodSignatureCheck(
                        context = context,
                        prefix = "spring_featured",
                        scopePackage = scope,
                        name = "[$root] Controller methods should return ResponseEntity",
                        description = "Controller methods in $root should expose ResponseEntity-based contracts",
                        constraint = ConstraintType.RETURN_TYPE,
                        severity = Severity.WARNING,
                        weight = 0.8,
                        methodNamePattern = "*",
                        returnType = org.springframework.http.ResponseEntity::class.java.name
                    )
                }
            }

            if (hasService) {
                rules += RuleFactory.classNaming(
                    context, "spring_featured", scope, root,
                    ClassType.SERVICE, "Service",
                    "[$root] Services should have 'Service' suffix",
                    "Services in $root should use the Service suffix",
                    Severity.INFO, 0.5
                )

                rules += RuleFactory.modifierCheck(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    name = "[$root] Services should be public",
                    description = "Service classes in $root should be public",
                    constraint = ConstraintType.SHOULD_BE_PUBLIC,
                    severity = Severity.INFO,
                    weight = 0.4,
                    scopeLabel = root
                )

                rules += RuleFactory.exceptionCheck(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    name = "[$root] Services should not throw generic Exception",
                    description = "Service layer in $root should avoid declaring generic Exception in throws clauses",
                    constraint = ConstraintType.SHOULD_NOT_THROW,
                    severity = Severity.WARNING,
                    weight = 0.8,
                    forbiddenExceptions = listOf("java.lang.Exception")
                )
            }

            if (hasRepository) {
                rules += RuleFactory.classNaming(
                    context, "spring_featured", scope, root,
                    ClassType.REPOSITORY, "Repository",
                    "[$root] Repositories should have 'Repository' suffix",
                    "Repositories in $root should use the Repository suffix",
                    Severity.INFO, 0.5
                )

                rules += RuleFactory.modifierCheck(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    name = "[$root] Repositories should be public",
                    description = "Repository classes in $root should be public",
                    constraint = ConstraintType.SHOULD_BE_PUBLIC,
                    severity = Severity.INFO,
                    weight = 0.4,
                    scopeLabel = root
                )
            }
        }

        return rules.distinctBy { it.id }
    }
}

@Service
class CleanProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.CLEAN

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        val domainPattern = com.example.archassistant.util.PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("domain", "core", "entity", "entities")
        ) ?: return emptyList()

        val applicationPattern = com.example.archassistant.util.PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("application", "usecase", "usecases", "interactor")
        )

        val infrastructurePattern = com.example.archassistant.util.PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("infrastructure", "persistence", "adapter", "gateway", "dataproviders", "repository")
        )

        val interfacePattern = com.example.archassistant.util.PackagePatternBuilder.compactPattern(
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

        val basePackage = context.basePackage
        if (basePackage.isNotBlank()) {
            val cyclePattern = "$basePackage.(*)..*"
            rules += RuleFactory.cycleCheck(
                context = context,
                prefix = "clean",
                scopePattern = cyclePattern,
                name = "Architecture should be free of package cycles",
                description = "Clean architecture layers should not form cyclic package dependencies",
                severity = Severity.CRITICAL,
                weight = 2.0
            )
        }

        if (infrastructurePattern != null) {
            rules += RuleFactory.inheritanceCheck(
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
                rules += RuleFactory.inheritanceCheck(
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

@Service
class HexagonalProfileStrategy : ProfileRuleStrategy {
    override val profile = ProjectProfile.HEXAGONAL

    override fun generate(context: RuleGenerationContext): List<ArchitecturalRule> {
        val domainPattern = com.example.archassistant.util.PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("domain", "core")
        ) ?: return emptyList()

        val applicationPattern = com.example.archassistant.util.PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("application", "usecase", "usecases", "interactor")
        )

        val portPattern = com.example.archassistant.util.PackagePatternBuilder.compactPattern(
            context.index.packagesContaining("port", "ports", "spi", "contract", "gateway", "gateways")
        )

        val adapterPattern = com.example.archassistant.util.PackagePatternBuilder.compactPattern(
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

        val basePackage = context.basePackage
        if (basePackage.isNotBlank()) {
            rules += RuleFactory.cycleCheck(
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
            rules += RuleFactory.interfaceCheck(
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
            rules += RuleFactory.interfaceCheck(
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
            rules += RuleFactory.inheritanceCheck(
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

            rules += RuleFactory.modifierCheck(
                context = context,
                prefix = "mvvm",
                scopePackage = vm,
                name = "ViewModel classes should be public",
                description = "ViewModel classes should be public",
                constraint = ConstraintType.SHOULD_BE_PUBLIC,
                severity = Severity.INFO,
                weight = 0.6,
                scopeLabel = vm
            )

            rules += RuleFactory.modifierCheck(
                context = context,
                prefix = "mvvm",
                scopePackage = vm,
                name = "ViewModel classes should be final",
                description = "ViewModel classes should be final when possible",
                constraint = ConstraintType.SHOULD_BE_FINAL,
                severity = Severity.WARNING,
                weight = 0.7,
                scopeLabel = vm
            )
        }

        views.forEach { view ->
            rules += RuleFactory.packageNaming(
                context, "mvvm", view, "View",
                "View packages should keep View naming",
                "View packages should keep View naming",
                Severity.INFO, 0.5
            )

            rules += RuleFactory.modifierCheck(
                context = context,
                prefix = "mvvm",
                scopePackage = view,
                name = "View classes should be public",
                description = "View classes should be public",
                constraint = ConstraintType.SHOULD_BE_PUBLIC,
                severity = Severity.INFO,
                weight = 0.4,
                scopeLabel = view
            )
        }

        return rules.distinctBy { it.id }
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

            rules += RuleFactory.modifierCheck(
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
            rules += RuleFactory.packageNaming(
                context, "modular", impl, "Impl",
                "Impl packages should end with Impl",
                "Impl packages should keep Impl naming",
                Severity.INFO, 0.5
            )

            rules += RuleFactory.modifierCheck(
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
            rules += RuleFactory.cycleCheck(
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

private fun shouldSuggestResponseEntityRules(context: RuleGenerationContext): Boolean {
    return context.structure.classes.any { info ->
        info.annotations.any { ann ->
            ann.removePrefix("@").equals("RestController", ignoreCase = true)
        } || info.publicMethods.any { method ->
            method.contains("ResponseEntity", ignoreCase = true)
        }
    }
}