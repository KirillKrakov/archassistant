package com.example.archassistant.util

import com.example.archassistant.model.*
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition

object ArchUnitRuleBuilder {

    fun build(rule: ArchitecturalRule): ArchRule? {
        return when (rule.type) {
            RuleType.DEPENDENCY -> buildDependencyRule(rule)
            RuleType.NAMING_CONVENTION -> buildNamingRule(rule)
            RuleType.ANNOTATION_CHECK -> buildAnnotationRule(rule)
            RuleType.LAYER_ISOLATION -> buildDependencyRule(rule)
            RuleType.CUSTOM -> null
        }
    }

    private fun buildDependencyRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val toPredicate = predicateFor(rule, isFrom = false)

        return when (rule.constraint) {
            ConstraintType.NO_DEPENDENCY ->
                ArchRuleDefinition.noClasses().that(fromPredicate).should().dependOnClassesThat(toPredicate)

            ConstraintType.MUST_DEPEND ->
                ArchRuleDefinition.classes().that(fromPredicate).should().dependOnClassesThat(toPredicate)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for DEPENDENCY rules")
        }.because(rule.description ?: rule.name)
    }

    private fun buildNamingRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val pattern = rule.pattern ?: ""

        return when (rule.constraint) {
            ConstraintType.NAMING_SUFFIX ->
                ArchRuleDefinition.classes().that(fromPredicate).should().haveSimpleNameEndingWith(pattern)
                    .because(rule.description ?: rule.name)

            ConstraintType.NAMING_PREFIX ->
                ArchRuleDefinition.classes().that(fromPredicate).should().haveSimpleNameStartingWith(pattern)
                    .because(rule.description ?: rule.name)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for NAMING_CONVENTION rules")
        }
    }

    private fun buildAnnotationRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val annotation = rule.annotation ?: ""

        return when (rule.constraint) {
            ConstraintType.HAS_ANNOTATION ->
                ArchRuleDefinition.classes().that(fromPredicate).should().beAnnotatedWith(annotation)
                    .because(rule.description ?: rule.name)

            ConstraintType.NO_ANNOTATION ->
                ArchRuleDefinition.noClasses().that(fromPredicate).should().beAnnotatedWith(annotation)
                    .because(rule.description ?: rule.name)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for ANNOTATION_CHECK rules")
        }
    }

    private fun predicateFor(rule: ArchitecturalRule, isFrom: Boolean): DescribedPredicate<JavaClass> {
        val selectorMode = if (isFrom) rule.fromSelectorMode else rule.toSelectorMode
        val classType = if (isFrom) rule.fromClassType else rule.toClassType
        val layerType = if (isFrom) rule.fromLayerType else rule.toLayerType
        val annotation = rule.annotation
        val packages: List<String> = if (isFrom) {
            listOf(rule.fromPackage)
        } else {
            rule.toPackages?.ifEmpty { null } ?: listOfNotNull(rule.toPackage)
        }

        val predicates = mutableListOf<DescribedPredicate<JavaClass>>()

        if (packages.isNotEmpty()) {
            predicates.add(JavaClass.Predicates.resideInAnyPackage(*packages.toTypedArray()))
        }

        when (selectorMode) {
            SelectorMode.CLASS_TYPE -> {
                if (classType != null) predicates.add(classTypePredicate(classType))
            }
            SelectorMode.LAYER -> {
                if (layerType != null) predicates.add(layerPredicate(layerType))
            }
            SelectorMode.ANNOTATION -> {
                if (!annotation.isNullOrBlank()) predicates.add(annotationPredicate(annotation))
            }
            SelectorMode.PACKAGE -> { }
        }

        return if (predicates.isEmpty()) {
            DescribedPredicate.alwaysTrue()
        } else {
            predicates.reduce { acc, predicate -> acc.and(predicate) }
        }
    }

    private fun packagePredicate(pattern: String): DescribedPredicate<JavaClass> {
        return object : DescribedPredicate<JavaClass>("reside in package pattern '$pattern'") {
            override fun test(item: JavaClass): Boolean {
                return PackagePatternBuilder.matches(pattern, item.packageName)
            }
        }
    }

    private fun annotationPredicate(annotation: String?): DescribedPredicate<JavaClass> {
        val normalized = annotation?.removePrefix("@")?.lowercase().orEmpty()
        return object : DescribedPredicate<JavaClass>("be annotated with '$annotation'") {
            override fun test(item: JavaClass): Boolean {
                if (normalized.isBlank()) return false
                return item.annotations.any { ann ->
                    ann.type.name.substringAfterLast('.').lowercase() == normalized
                }
            }
        }
    }

    private fun classTypePredicate(type: ClassType?): DescribedPredicate<JavaClass> {
        val resolved = type ?: return neverPredicate("match no class type")
        return object : DescribedPredicate<JavaClass>("match class type '$resolved'") {
            override fun test(item: JavaClass): Boolean = ProjectLayerClassifier.matchesClassType(item, resolved)
        }
    }

    private fun layerPredicate(type: LayerType?): DescribedPredicate<JavaClass> {
        val resolved = type ?: return neverPredicate("match no layer")
        return object : DescribedPredicate<JavaClass>("match layer '$resolved'") {
            override fun test(item: JavaClass): Boolean = ProjectLayerClassifier.matchesLayer(item, resolved)
        }
    }

    private fun and(
        left: DescribedPredicate<JavaClass>,
        right: DescribedPredicate<JavaClass>
    ): DescribedPredicate<JavaClass> {
        return object : DescribedPredicate<JavaClass>("(${left.description}) and (${right.description})") {
            override fun test(item: JavaClass): Boolean = left.test(item) && right.test(item)
        }
    }

    private fun alwaysTruePredicate(): DescribedPredicate<JavaClass> {
        return object : DescribedPredicate<JavaClass>("always true") {
            override fun test(item: JavaClass): Boolean = true
        }
    }

    private fun neverPredicate(description: String): DescribedPredicate<JavaClass> {
        return object : DescribedPredicate<JavaClass>(description) {
            override fun test(item: JavaClass): Boolean = false
        }
    }
}