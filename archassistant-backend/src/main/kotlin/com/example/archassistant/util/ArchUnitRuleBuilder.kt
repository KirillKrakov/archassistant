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

        val base = when (rule.constraint) {
            ConstraintType.NO_DEPENDENCY ->
                ArchRuleDefinition.noClasses().that(fromPredicate).should().dependOnClassesThat(toPredicate)

            ConstraintType.MUST_DEPEND ->
                ArchRuleDefinition.classes().that(fromPredicate).should().dependOnClassesThat(toPredicate)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for DEPENDENCY rules")
        }

        return base.because(rule.description ?: rule.name)
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
        val mode = if (isFrom) rule.fromSelectorMode else rule.toSelectorMode
        return when (mode) {
            SelectorMode.PACKAGE -> {
                val patterns = if (isFrom) {
                    listOf(rule.fromPackage)
                } else {
                    when {
                        !rule.toPackages.isNullOrEmpty() -> rule.toPackages
                        !rule.toPackage.isNullOrBlank() -> listOf(rule.toPackage)
                        else -> listOf(rule.fromPackage)
                    }
                }
                packagePredicate(patterns)
            }

            SelectorMode.CLASS_TYPE -> {
                val type = if (isFrom) rule.fromClassType else rule.toClassType
                classTypePredicate(type)
            }

            SelectorMode.LAYER -> {
                val type = if (isFrom) rule.fromLayerType else rule.toLayerType
                layerPredicate(type)
            }

            SelectorMode.ANNOTATION -> annotationPredicate(rule.annotation)
        }
    }

    private fun packagePredicate(patterns: List<String>): DescribedPredicate<JavaClass> {
        val normalized = patterns.map { it.trim() }.filter { it.isNotBlank() }

        return object : DescribedPredicate<JavaClass>("reside in package pattern '${normalized.joinToString(", ")}'") {
            override fun test(item: JavaClass): Boolean {
                if (normalized.isEmpty()) return false
                return normalized.any { PackagePatternBuilder.matches(it, item.packageName) }
            }
        }
    }

    private fun classTypePredicate(type: ClassType?): DescribedPredicate<JavaClass> {
        val resolved = type ?: return object : DescribedPredicate<JavaClass>("match no class type") {
            override fun test(item: JavaClass): Boolean = false
        }

        return object : DescribedPredicate<JavaClass>("match class type '$resolved'") {
            override fun test(item: JavaClass): Boolean = ProjectLayerClassifier.matchesClassType(item, resolved)
        }
    }

    private fun layerPredicate(type: LayerType?): DescribedPredicate<JavaClass> {
        val resolved = type ?: return object : DescribedPredicate<JavaClass>("match no layer") {
            override fun test(item: JavaClass): Boolean = false
        }

        return object : DescribedPredicate<JavaClass>("match layer '$resolved'") {
            override fun test(item: JavaClass): Boolean = ProjectLayerClassifier.matchesLayer(item, resolved)
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
}