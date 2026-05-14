package com.example.archassistant.service.rules.profile

import com.example.archassistant.model.*
import com.example.archassistant.service.rules.ProfileRuleStrategy
import com.example.archassistant.service.rules.RuleGenerationContext
import com.example.archassistant.service.rules.profile.common.*
import com.example.archassistant.service.rules.profile.common.addClassDependencyRule
import com.example.archassistant.service.rules.profile.common.addClassNamingRule
import com.example.archassistant.service.rules.profile.common.addModifierRule
import com.example.archassistant.service.rules.profile.common.shouldSuggestResponseEntityRules
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

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
                rules.addClassDependencyRule(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    scopeLabel = root,
                    name = "[$root] Controllers should depend on services",
                    description = "Controller classes in $root should call services within the same feature",
                    fromType = ClassType.CONTROLLER,
                    toType = ClassType.SERVICE,
                    constraint = ConstraintType.MUST_DEPEND,
                    severity = Severity.INFO,
                    weight = 1.0
                )
            }

            if (hasController && hasRepository) {
                rules.addClassDependencyRule(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    scopeLabel = root,
                    name = "[$root] Controllers should not depend on repositories",
                    description = "Controller classes in $root should not access repositories directly",
                    fromType = ClassType.CONTROLLER,
                    toType = ClassType.REPOSITORY,
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.ERROR,
                    weight = 1.5
                )
            }

            if (hasService && hasController) {
                rules.addClassDependencyRule(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    scopeLabel = root,
                    name = "[$root] Services should not depend on controllers",
                    description = "Service layer in $root should remain independent from presentation",
                    fromType = ClassType.SERVICE,
                    toType = ClassType.CONTROLLER,
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.CRITICAL,
                    weight = 2.0
                )
            }

            if (hasRepository && hasService) {
                rules.addClassDependencyRule(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    scopeLabel = root,
                    name = "[$root] Repositories should not depend on services",
                    description = "Repository layer in $root should stay below the service layer",
                    fromType = ClassType.REPOSITORY,
                    toType = ClassType.SERVICE,
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.CRITICAL,
                    weight = 2.0
                )
            }

            if (hasController) {
                rules.addClassNamingRule(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    scopeLabel = root,
                    classType = ClassType.CONTROLLER,
                    suffix = "Controller",
                    name = "[$root] Controllers should have 'Controller' suffix",
                    description = "Controllers in $root should use the Controller suffix",
                    severity = Severity.INFO,
                    weight = 0.5
                )

                rules.addModifierRule(
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

                if (shouldSuggestResponseEntityRules(context, root)) {
                    rules.addMethodSignatureRule(
                        context = context,
                        prefix = "spring_featured",
                        scopePackage = scope,
                        name = "[$root] Controller methods should return ResponseEntity",
                        description = "Controller methods in $root should expose ResponseEntity-based contracts",
                        constraint = ConstraintType.RETURN_TYPE,
                        severity = Severity.WARNING,
                        weight = 0.8,
                        methodNamePattern = "*",
                        returnType = ResponseEntity::class.java.name
                    )
                }
            }

            if (hasService) {
                rules.addClassNamingRule(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    scopeLabel = root,
                    classType = ClassType.SERVICE,
                    suffix = "Service",
                    name = "[$root] Services should have 'Service' suffix",
                    description = "Services in $root should use the Service suffix",
                    severity = Severity.INFO,
                    weight = 0.5
                )

                rules.addModifierRule(
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

                rules.addExceptionRule(
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
                rules.addClassNamingRule(
                    context = context,
                    prefix = "spring_featured",
                    scopePackage = scope,
                    scopeLabel = root,
                    classType = ClassType.REPOSITORY,
                    suffix = "Repository",
                    name = "[$root] Repositories should have 'Repository' suffix",
                    description = "Repositories in $root should use the Repository suffix",
                    severity = Severity.INFO,
                    weight = 0.5
                )

                rules.addModifierRule(
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