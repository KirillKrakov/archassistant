package com.example.archassistant.service.rules.profile

import com.example.archassistant.model.ClassType
import com.example.archassistant.model.Severity
import com.example.archassistant.model.context.ProjectProfile
import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.model.rules.ConstraintType
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
            rules.addClassDependencyRule(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                scopeLabel = "all",
                name = "Controllers should depend on services",
                description = "Controllers should call services rather than contain business logic",
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
                prefix = "spring_layered",
                scopePackage = scope,
                scopeLabel = "all",
                name = "Controllers should not depend on repositories",
                description = "Controllers should not access repositories directly",
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
                prefix = "spring_layered",
                scopePackage = scope,
                scopeLabel = "all",
                name = "Services should not depend on controllers",
                description = "Service layer should remain independent from presentation",
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
                prefix = "spring_layered",
                scopePackage = scope,
                scopeLabel = "all",
                name = "Repositories should not depend on services",
                description = "Data access layer should not depend on business logic",
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
                prefix = "spring_layered",
                scopePackage = scope,
                scopeLabel = "all",
                classType = ClassType.CONTROLLER,
                suffix = "Controller",
                name = "Controllers should have 'Controller' suffix",
                description = "Controllers should use the Controller suffix",
                severity = Severity.INFO,
                weight = 0.5
            )

            rules.addModifierRule(
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

            rules.addAnnotationHasRule(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                annotation = "org.springframework.web.bind.annotation.RestController",
                name = "Controllers should be annotated with @RestController",
                description = "Controller classes should be annotated with @RestController",
                severity = Severity.INFO,
                weight = 0.7
            )

            if (shouldSuggestResponseEntityRules(context)) {
                rules.addMethodSignatureRule(
                    context = context,
                    prefix = "spring_layered",
                    scopePackage = scope,
                    name = "Controller methods should return ResponseEntity",
                    description = "Public controller methods should expose ResponseEntity-based contracts",
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
                prefix = "spring_layered",
                scopePackage = scope,
                scopeLabel = "all",
                classType = ClassType.SERVICE,
                suffix = "Service",
                name = "Services should have 'Service' suffix",
                description = "Services should use the Service suffix",
                severity = Severity.INFO,
                weight = 0.5
            )

            rules.addModifierRule(
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

            rules.addAnnotationHasRule(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                annotation = "org.springframework.stereotype.Service",
                name = "Services should be annotated with @Service",
                description = "Service classes should be annotated with @Service",
                severity = Severity.INFO,
                weight = 0.7
            )

            rules.addExceptionRule(
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
            rules.addClassNamingRule(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                scopeLabel = "all",
                classType = ClassType.REPOSITORY,
                suffix = "Repository",
                name = "Repositories should have 'Repository' suffix",
                description = "Repositories should use the Repository suffix",
                severity = Severity.INFO,
                weight = 0.5
            )

            rules.addModifierRule(
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

            rules.addAnnotationHasRule(
                context = context,
                prefix = "spring_layered",
                scopePackage = scope,
                annotation = "org.springframework.stereotype.Repository",
                name = "Repositories should be annotated with @Repository",
                description = "Repository classes should be annotated with @Repository",
                severity = Severity.INFO,
                weight = 0.7
            )
        }

        return rules.distinctBy { it.id }
    }
}