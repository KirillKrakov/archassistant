package com.example.archassistant.service.templates

import com.example.archassistant.model.*
import com.example.archassistant.util.ProjectLayerClassifier

object SpringFeatureRules {

    fun generate(structure: ProjectStructure, basePackage: String): List<ArchitecturalRule> {
        if (!structure.isSpringLike()) return emptyList()

        val roots = structure.featureRoots(basePackage)
        if (roots.isEmpty()) return emptyList()

        val rules = mutableListOf<ArchitecturalRule>()

        for (root in roots) {
            val scope = if (basePackage.isBlank()) root else "$basePackage.$root"
            val scopedClasses = structure.classes.filter { it.packageName.startsWith(scope) }

            val hasController = scopedClasses.any { ProjectLayerClassifier.matchesClassType(it, ClassType.CONTROLLER) }
            val hasService = scopedClasses.any { ProjectLayerClassifier.matchesClassType(it, ClassType.SERVICE) }
            val hasRepository = scopedClasses.any { ProjectLayerClassifier.matchesClassType(it, ClassType.REPOSITORY) }

            if (hasController && hasService) {
                rules += ArchitecturalRule(
                    id = "spring_${root}_controller_must_depend_on_service_${structure.projectId}",
                    name = "[$root] Controllers should depend on services",
                    description = "Controller classes in $root should call services within the same feature",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "$scope..*",
                    toPackage = "$scope..*",
                    constraint = ConstraintType.MUST_DEPEND,
                    fromSelectorMode = SelectorMode.CLASS_TYPE,
                    toSelectorMode = SelectorMode.CLASS_TYPE,
                    fromClassType = ClassType.CONTROLLER,
                    toClassType = ClassType.SERVICE,
                    severity = Severity.INFO,
                    weight = 1.0,
                    enabled = true,
                    suggested = true
                )
            }

            if (hasController && hasRepository) {
                rules += ArchitecturalRule(
                    id = "spring_${root}_controller_no_repository_${structure.projectId}",
                    name = "[$root] Controllers should not depend on repositories",
                    description = "Controller classes in $root should not access repositories directly",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "$scope..*",
                    toPackage = "$scope..*",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    fromSelectorMode = SelectorMode.CLASS_TYPE,
                    toSelectorMode = SelectorMode.CLASS_TYPE,
                    fromClassType = ClassType.CONTROLLER,
                    toClassType = ClassType.REPOSITORY,
                    severity = Severity.ERROR,
                    weight = 1.5,
                    enabled = true,
                    suggested = true
                )
            }

            if (hasService && hasController) {
                rules += ArchitecturalRule(
                    id = "spring_${root}_service_no_controller_${structure.projectId}",
                    name = "[$root] Services should not depend on controllers",
                    description = "Service layer in $root should remain independent from presentation",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "$scope..*",
                    toPackage = "$scope..*",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    fromSelectorMode = SelectorMode.CLASS_TYPE,
                    toSelectorMode = SelectorMode.CLASS_TYPE,
                    fromClassType = ClassType.SERVICE,
                    toClassType = ClassType.CONTROLLER,
                    severity = Severity.CRITICAL,
                    weight = 2.0,
                    enabled = true,
                    suggested = true
                )
            }

            if (hasRepository && hasService) {
                rules += ArchitecturalRule(
                    id = "spring_${root}_repository_no_service_${structure.projectId}",
                    name = "[$root] Repositories should not depend on services",
                    description = "Repository layer in $root should stay below the service layer",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "$scope..*",
                    toPackage = "$scope..*",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    fromSelectorMode = SelectorMode.CLASS_TYPE,
                    toSelectorMode = SelectorMode.CLASS_TYPE,
                    fromClassType = ClassType.REPOSITORY,
                    toClassType = ClassType.SERVICE,
                    severity = Severity.CRITICAL,
                    weight = 2.0,
                    enabled = true,
                    suggested = true
                )
            }
        }

        return rules
    }
}