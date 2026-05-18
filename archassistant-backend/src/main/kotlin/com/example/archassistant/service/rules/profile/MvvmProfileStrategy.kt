package com.example.archassistant.service.rules.profile

import com.example.archassistant.model.core.Severity
import com.example.archassistant.model.context.ProjectProfile
import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.model.rules.ConstraintType
import com.example.archassistant.service.rules.RuleGenerationContext
import com.example.archassistant.service.rules.profile.common.addFieldRule
import com.example.archassistant.service.rules.profile.common.addModifierRule
import com.example.archassistant.service.rules.profile.common.addPackageDependencyRule
import com.example.archassistant.service.rules.profile.common.addPackageNamingRule
import org.springframework.stereotype.Service

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
                rules.addPackageDependencyRule(
                    context = context,
                    prefix = "mvvm",
                    fromPackage = vm,
                    toPackage = view,
                    name = "ViewModels should not depend on Views",
                    description = "ViewModel must not depend on UI components",
                    severity = Severity.CRITICAL,
                    weight = 2.0
                )
            }

            rules.addPackageNamingRule(
                context = context,
                prefix = "mvvm",
                scopePackage = vm,
                suffix = "ViewModel",
                name = "ViewModel packages should end with ViewModel",
                description = "ViewModel packages should keep ViewModel naming",
                severity = Severity.INFO,
                weight = 0.5
            )

            rules.addModifierRule(
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

            rules.addModifierRule(
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

            rules.addFieldRule(
                context = context,
                prefix = "mvvm",
                scopePackage = vm,
                name = "ViewModel fields should use state-oriented naming",
                description = "ViewModel fields should use state-oriented naming conventions",
                constraint = ConstraintType.FIELD_NAME_PATTERN,
                severity = Severity.INFO,
                weight = 0.4,
                fieldNamePattern = "*State"
            )
        }

        views.forEach { view ->
            rules.addPackageNamingRule(
                context = context,
                prefix = "mvvm",
                scopePackage = view,
                suffix = "View",
                name = "View packages should keep View naming",
                description = "View packages should keep View naming",
                severity = Severity.INFO,
                weight = 0.5
            )

            rules.addModifierRule(
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