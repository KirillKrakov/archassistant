package com.example.archassistant.service.rules

import com.example.archassistant.model.*
import java.security.MessageDigest

object RuleFactory {

    fun classDependency(
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
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopeLabel, fromType.name, toType.name, constraint.name, name),
            name = name,
            description = description,
            type = RuleType.DEPENDENCY,
            fromPackage = scopePackage,
            toPackage = scopePackage,
            constraint = constraint,
            fromSelectorMode = SelectorMode.CLASS_TYPE,
            toSelectorMode = SelectorMode.CLASS_TYPE,
            fromClassType = fromType,
            toClassType = toType,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun packageDependency(
        context: RuleGenerationContext,
        prefix: String,
        fromPackage: String,
        toPackage: String,
        name: String,
        description: String,
        constraint: ConstraintType,
        severity: Severity,
        weight: Double
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, fromPackage, toPackage, constraint.name, name),
            name = name,
            description = description,
            type = RuleType.DEPENDENCY,
            fromPackage = fromPackage,
            toPackage = toPackage,
            constraint = constraint,
            fromSelectorMode = SelectorMode.PACKAGE,
            toSelectorMode = SelectorMode.PACKAGE,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun packageNaming(
        context: RuleGenerationContext,
        prefix: String,
        scopePackage: String,
        suffix: String,
        name: String,
        description: String,
        severity: Severity,
        weight: Double
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopePackage, suffix, name),
            name = name,
            description = description,
            type = RuleType.NAMING_CONVENTION,
            fromPackage = scopePackage,
            constraint = ConstraintType.NAMING_SUFFIX,
            pattern = suffix,
            fromSelectorMode = SelectorMode.PACKAGE,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun classNaming(
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
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopeLabel, classType.name, suffix, name),
            name = name,
            description = description,
            type = RuleType.NAMING_CONVENTION,
            fromPackage = scopePackage,
            constraint = ConstraintType.NAMING_SUFFIX,
            pattern = suffix,
            fromSelectorMode = SelectorMode.CLASS_TYPE,
            fromClassType = classType,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun annotationNo(
        context: RuleGenerationContext,
        prefix: String,
        scopePackage: String,
        annotation: String,
        name: String,
        description: String,
        severity: Severity,
        weight: Double
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopePackage, annotation, "NO_ANNOTATION"),
            name = name,
            description = description,
            type = RuleType.ANNOTATION_CHECK,
            fromPackage = scopePackage,
            constraint = ConstraintType.NO_ANNOTATION,
            annotation = annotation,
            fromSelectorMode = SelectorMode.ANNOTATION,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun annotationHas(
        context: RuleGenerationContext,
        prefix: String,
        scopePackage: String,
        annotation: String,
        name: String,
        description: String,
        severity: Severity,
        weight: Double
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopePackage, annotation, "HAS_ANNOTATION"),
            name = name,
            description = description,
            type = RuleType.ANNOTATION_CHECK,
            fromPackage = scopePackage,
            constraint = ConstraintType.HAS_ANNOTATION,
            annotation = annotation,
            fromSelectorMode = SelectorMode.ANNOTATION,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun cycleCheck(
        context: RuleGenerationContext,
        prefix: String,
        scopePattern: String,
        name: String,
        description: String,
        severity: Severity,
        weight: Double
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopePattern, "CYCLE", name),
            name = name,
            description = description,
            type = RuleType.CYCLE_CHECK,
            fromPackage = scopePattern,
            constraint = ConstraintType.NO_CYCLE,
            slicePattern = scopePattern,
            fromSelectorMode = SelectorMode.PACKAGE,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun inheritanceCheck(
        context: RuleGenerationContext,
        prefix: String,
        fromPackage: String,
        toPackage: String,
        name: String,
        description: String,
        constraint: ConstraintType,
        severity: Severity,
        weight: Double
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, fromPackage, toPackage, constraint.name, name),
            name = name,
            description = description,
            type = RuleType.INHERITANCE_CHECK,
            fromPackage = fromPackage,
            toPackage = toPackage,
            constraint = constraint,
            fromSelectorMode = SelectorMode.PACKAGE,
            toSelectorMode = SelectorMode.PACKAGE,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun interfaceCheck(
        context: RuleGenerationContext,
        prefix: String,
        fromPackage: String,
        toPackage: String,
        name: String,
        description: String,
        constraint: ConstraintType,
        severity: Severity,
        weight: Double
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, fromPackage, toPackage, constraint.name, name),
            name = name,
            description = description,
            type = RuleType.INTERFACE_CHECK,
            fromPackage = fromPackage,
            toPackage = toPackage,
            constraint = constraint,
            fromSelectorMode = SelectorMode.PACKAGE,
            toSelectorMode = SelectorMode.PACKAGE,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun modifierCheck(
        context: RuleGenerationContext,
        prefix: String,
        scopePackage: String,
        name: String,
        description: String,
        constraint: ConstraintType,
        severity: Severity,
        weight: Double,
        scopeLabel: String = "all"
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopeLabel, scopePackage, constraint.name, name),
            name = name,
            description = description,
            type = RuleType.MODIFIER_CHECK,
            fromPackage = scopePackage,
            constraint = constraint,
            fromSelectorMode = SelectorMode.PACKAGE,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun methodSignatureCheck(
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
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopePackage, constraint.name, name),
            name = name,
            description = description,
            type = RuleType.METHOD_SIGNATURE_CHECK,
            fromPackage = scopePackage,
            constraint = constraint,
            fromSelectorMode = SelectorMode.PACKAGE,
            fromMethodNamePattern = methodNamePattern,
            fromReturnType = returnType,
            fromParameterTypes = parameterTypes,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun fieldCheck(
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
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopePackage, constraint.name, name),
            name = name,
            description = description,
            type = RuleType.FIELD_CHECK,
            fromPackage = scopePackage,
            constraint = constraint,
            fromSelectorMode = SelectorMode.PACKAGE,
            fromFieldNamePattern = fieldNamePattern,
            fromFieldType = fieldType,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    fun exceptionCheck(
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
    ): ArchitecturalRule {
        return ArchitecturalRule(
            id = stableId(prefix, context.projectId, scopePackage, constraint.name, name),
            name = name,
            description = description,
            type = RuleType.EXCEPTION_CHECK,
            fromPackage = scopePackage,
            constraint = constraint,
            fromSelectorMode = SelectorMode.PACKAGE,
            fromThrowsTypes = allowedExceptions ?: forbiddenExceptions,
            severity = severity,
            weight = weight,
            enabled = true,
            suggested = true
        )
    }

    private fun stableId(prefix: String, projectId: String, vararg parts: String): String {
        val readable = parts
            .joinToString("_") { part ->
                part.replace("[^A-Za-z0-9]+".toRegex(), "_").trim('_')
            }
            .replace("__", "_")

        val hash = sha1(listOf(prefix, projectId, readable).joinToString("|")).take(12)
        return listOf(prefix, projectId, readable, hash)
            .filter { it.isNotBlank() }
            .joinToString("_")
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}