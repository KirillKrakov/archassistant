package com.example.archassistant.service.rules.profile.common

import com.example.archassistant.model.context.ClassType
import com.example.archassistant.model.core.Severity
import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.model.rules.ConstraintType
import com.example.archassistant.service.rules.RuleFactory
import com.example.archassistant.service.rules.RuleGenerationContext

internal fun MutableList<ArchitecturalRule>.addClassDependencyRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    scopeLabel: String,
    name: String,
    description: String,
    fromType: ClassType,
    toType: ClassType,
    constraint: ConstraintType,
    severity: Severity,
    weight: Double
) {
    add(
        RuleFactory.classDependency(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            scopeLabel = scopeLabel,
            name = name,
            description = description,
            fromType = fromType,
            toType = toType,
            constraint = constraint,
            severity = severity,
            weight = weight
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addPackageDependencyRule(
    context: RuleGenerationContext,
    prefix: String,
    fromPackage: String,
    toPackage: String,
    name: String,
    description: String,
    constraint: ConstraintType = ConstraintType.NO_DEPENDENCY,
    severity: Severity,
    weight: Double
) {
    if (fromPackage == toPackage) return

    add(
        RuleFactory.packageDependency(
            context = context,
            prefix = prefix,
            fromPackage = fromPackage,
            toPackage = toPackage,
            name = name,
            description = description,
            constraint = constraint,
            severity = severity,
            weight = weight
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addPackageNamingRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    suffix: String,
    name: String,
    description: String,
    severity: Severity,
    weight: Double
) {
    add(
        RuleFactory.packageNaming(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            suffix = suffix,
            name = name,
            description = description,
            severity = severity,
            weight = weight
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addClassNamingRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    scopeLabel: String,
    classType: ClassType,
    suffix: String,
    name: String,
    description: String,
    severity: Severity,
    weight: Double
) {
    add(
        RuleFactory.classNaming(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            scopeLabel = scopeLabel,
            classType = classType,
            suffix = suffix,
            name = name,
            description = description,
            severity = severity,
            weight = weight
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addAnnotationHasRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    annotation: String,
    name: String,
    description: String,
    severity: Severity,
    weight: Double
) {
    add(
        RuleFactory.annotationHas(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            annotation = annotation,
            name = name,
            description = description,
            severity = severity,
            weight = weight
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addAnnotationNoRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    annotation: String,
    name: String,
    description: String,
    severity: Severity,
    weight: Double
) {
    add(
        RuleFactory.annotationNo(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            annotation = annotation,
            name = name,
            description = description,
            severity = severity,
            weight = weight
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addModifierRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    name: String,
    description: String,
    constraint: ConstraintType,
    severity: Severity,
    weight: Double,
    scopeLabel: String = "all"
) {
    add(
        RuleFactory.modifierCheck(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            name = name,
            description = description,
            constraint = constraint,
            severity = severity,
            weight = weight,
            scopeLabel = scopeLabel
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addMethodSignatureRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    name: String,
    description: String,
    constraint: ConstraintType,
    severity: Severity,
    weight: Double,
    methodNamePattern: String? = null,
    returnType: String? = null,
    parameterTypes: List<String>? = null
) {
    add(
        RuleFactory.methodSignatureCheck(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            name = name,
            description = description,
            constraint = constraint,
            severity = severity,
            weight = weight,
            methodNamePattern = methodNamePattern,
            returnType = returnType,
            parameterTypes = parameterTypes
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addFieldRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    name: String,
    description: String,
    constraint: ConstraintType,
    severity: Severity,
    weight: Double,
    fieldNamePattern: String? = null,
    fieldType: String? = null
) {
    add(
        RuleFactory.fieldCheck(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            name = name,
            description = description,
            constraint = constraint,
            severity = severity,
            weight = weight,
            fieldNamePattern = fieldNamePattern,
            fieldType = fieldType
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addExceptionRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePackage: String,
    name: String,
    description: String,
    constraint: ConstraintType,
    severity: Severity,
    weight: Double,
    allowedExceptions: List<String>? = null,
    forbiddenExceptions: List<String>? = null
) {
    add(
        RuleFactory.exceptionCheck(
            context = context,
            prefix = prefix,
            scopePackage = scopePackage,
            name = name,
            description = description,
            constraint = constraint,
            severity = severity,
            weight = weight,
            allowedExceptions = allowedExceptions,
            forbiddenExceptions = forbiddenExceptions
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addInheritanceRule(
    context: RuleGenerationContext,
    prefix: String,
    fromPackage: String,
    toPackage: String,
    name: String,
    description: String,
    constraint: ConstraintType,
    severity: Severity,
    weight: Double
) {
    add(
        RuleFactory.inheritanceCheck(
            context = context,
            prefix = prefix,
            fromPackage = fromPackage,
            toPackage = toPackage,
            name = name,
            description = description,
            constraint = constraint,
            severity = severity,
            weight = weight
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addInterfaceRule(
    context: RuleGenerationContext,
    prefix: String,
    fromPackage: String,
    toPackage: String,
    name: String,
    description: String,
    constraint: ConstraintType,
    severity: Severity,
    weight: Double
) {
    add(
        RuleFactory.interfaceCheck(
            context = context,
            prefix = prefix,
            fromPackage = fromPackage,
            toPackage = toPackage,
            name = name,
            description = description,
            constraint = constraint,
            severity = severity,
            weight = weight
        )
    )
}

internal fun MutableList<ArchitecturalRule>.addCycleRule(
    context: RuleGenerationContext,
    prefix: String,
    scopePattern: String,
    name: String,
    description: String,
    severity: Severity,
    weight: Double
) {
    add(
        RuleFactory.cycleCheck(
            context = context,
            prefix = prefix,
            scopePattern = scopePattern,
            name = name,
            description = description,
            severity = severity,
            weight = weight
        )
    )
}

internal fun shouldSuggestResponseEntityRules(
    context: RuleGenerationContext,
    root: String? = null
): Boolean {
    val classes = if (root.isNullOrBlank()) {
        context.structure.classes
    } else {
        context.index.classesInRoot(root)
    }

    return classes.any { info ->
        info.annotations.any { ann ->
            ann.removePrefix("@").equals("RestController", ignoreCase = true)
        } || info.publicMethods.any { method ->
            method.contains("ResponseEntity", ignoreCase = true)
        }
    }
}