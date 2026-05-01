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