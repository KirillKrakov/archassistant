package com.example.archassistant.util

import com.example.archassistant.model.*
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition

/**
 * Фабрика для создания ArchUnit правил из декларативных моделей
 */
object ArchUnitRuleBuilder {

    /**
     * Создание ArchRule из ArchitecturalRule
     */
    fun build(rule: ArchitecturalRule): ArchRule? {
        return when (rule.type) {
            RuleType.DEPENDENCY -> buildDependencyRule(rule)
            RuleType.NAMING_CONVENTION -> buildNamingRule(rule)
            RuleType.ANNOTATION_CHECK -> buildAnnotationRule(rule)
            RuleType.LAYER_ISOLATION -> buildLayerIsolationRule(rule)
            RuleType.CUSTOM -> {
                println("Custom rules are not supported yet: ${rule.id}")
                null
            }
        }
    }

    /**
     * Правило: зависимости между классами/пакетами/слоями
     */
    private fun buildDependencyRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val toPredicate = predicateFor(rule, isFrom = false)

        val baseRule = when (rule.constraint) {
            ConstraintType.NO_DEPENDENCY ->
                ArchRuleDefinition.noClasses()
                    .that(fromPredicate)
                    .should()
                    .dependOnClassesThat(toPredicate)

            ConstraintType.MUST_DEPEND ->
                ArchRuleDefinition.classes()
                    .that(fromPredicate)
                    .should()
                    .dependOnClassesThat(toPredicate)

            else -> throw UnsupportedOperationException(
                "Constraint ${rule.constraint} not supported for DEPENDENCY rules"
            )
        }

        return baseRule.because(rule.description ?: rule.name)
    }

    /**
     * Правило именования
     */
    private fun buildNamingRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val pattern = rule.pattern ?: ""

        return when (rule.constraint) {
            ConstraintType.NAMING_SUFFIX ->
                ArchRuleDefinition.classes()
                    .that(fromPredicate)
                    .should().haveSimpleNameEndingWith(pattern)
                    .because(rule.description ?: rule.name)

            ConstraintType.NAMING_PREFIX ->
                ArchRuleDefinition.classes()
                    .that(fromPredicate)
                    .should().haveSimpleNameStartingWith(pattern)
                    .because(rule.description ?: rule.name)

            else -> throw UnsupportedOperationException(
                "Constraint ${rule.constraint} not supported for NAMING_CONVENTION rules"
            )
        }
    }

    /**
     * Правило аннотаций
     */
    private fun buildAnnotationRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val annotation = rule.annotation ?: ""

        return when (rule.constraint) {
            ConstraintType.HAS_ANNOTATION ->
                ArchRuleDefinition.classes()
                    .that(fromPredicate)
                    .should().beAnnotatedWith(annotation)
                    .because(rule.description ?: rule.name)

            ConstraintType.NO_ANNOTATION ->
                ArchRuleDefinition.noClasses()
                    .that(fromPredicate)
                    .should().beAnnotatedWith(annotation)
                    .because(rule.description ?: rule.name)

            else -> throw UnsupportedOperationException(
                "Constraint ${rule.constraint} not supported for ANNOTATION_CHECK rules"
            )
        }
    }

    /**
     * Изоляция слоя — частный случай dependency rule
     */
    private fun buildLayerIsolationRule(rule: ArchitecturalRule): ArchRule {
        return buildDependencyRule(rule)
    }

    private fun predicateFor(rule: ArchitecturalRule, isFrom: Boolean): DescribedPredicate<JavaClass> {
        return when (if (isFrom) rule.fromSelectorMode else rule.toSelectorMode) {
            SelectorMode.PACKAGE -> packagePredicate(
                if (isFrom) {
                    listOf(rule.fromPackage)
                } else {
                    targetPackagePatterns(rule)
                }
            )

            SelectorMode.CLASS_TYPE -> classTypePredicate(
                if (isFrom) rule.fromClassType else rule.toClassType
            )

            SelectorMode.LAYER -> layerPredicate(
                if (isFrom) rule.fromLayerType else rule.toLayerType
            )

            SelectorMode.ANNOTATION -> annotationPredicate(rule.annotation)
        }
    }

    private fun targetPackagePatterns(rule: ArchitecturalRule): List<String> {
        return when {
            !rule.toPackages.isNullOrEmpty() -> rule.toPackages
            !rule.toPackage.isNullOrBlank() -> listOf(rule.toPackage)
            else -> listOf(rule.fromPackage)
        }
    }

    private fun packagePredicate(patterns: List<String>): DescribedPredicate<JavaClass> {
        val normalizedPatterns = patterns
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return object : DescribedPredicate<JavaClass>("reside in package pattern '${normalizedPatterns.joinToString(", ")}'") {
            override fun test(item: JavaClass): Boolean {
                if (normalizedPatterns.isEmpty()) return false
                return normalizedPatterns.any { pattern ->
                    PackagePatternBuilder.matches(pattern, item.packageName)
                }
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
        val resolved = type ?: return object : DescribedPredicate<JavaClass>("match no class type") {
            override fun test(item: JavaClass): Boolean = false
        }

        return object : DescribedPredicate<JavaClass>("match class type '$resolved'") {
            override fun test(item: JavaClass): Boolean {
                return ProjectLayerClassifier.matchesClassType(item, resolved)
            }
        }
    }

    private fun layerPredicate(type: LayerType?): DescribedPredicate<JavaClass> {
        val resolved = type ?: return object : DescribedPredicate<JavaClass>("match no layer") {
            override fun test(item: JavaClass): Boolean = false
        }

        return object : DescribedPredicate<JavaClass>("match layer '$resolved'") {
            override fun test(item: JavaClass): Boolean {
                return ProjectLayerClassifier.classify(item) == resolved
            }
        }
    }
}