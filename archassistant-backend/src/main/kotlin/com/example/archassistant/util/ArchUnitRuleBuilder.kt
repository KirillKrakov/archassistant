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
     * Правило: зависимости между классами/пакетами
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
                if (isFrom) rule.fromPackage else targetPackagePattern(rule)
            )

            SelectorMode.CLASS_TYPE -> classTypePredicate(
                if (isFrom) rule.fromClassType else rule.toClassType
            )

            SelectorMode.ANNOTATION -> annotationPredicate(rule.annotation)
        }
    }

    private fun targetPackagePattern(rule: ArchitecturalRule): String {
        return when {
            !rule.toPackages.isNullOrEmpty() -> rule.toPackages.joinToString("|")
            !rule.toPackage.isNullOrBlank() -> rule.toPackage
            else -> rule.fromPackage
        }
    }

    private fun packagePredicate(pattern: String): DescribedPredicate<JavaClass> {
        val regex = wildcardToRegex(pattern).toRegex()
        return object : DescribedPredicate<JavaClass>("reside in package pattern '$pattern'") {
            override fun test(item: JavaClass): Boolean {
                return regex.matches(item.packageName)
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
        val resolved = type ?: return packagePredicate("..*")
        return object : DescribedPredicate<JavaClass>("match class type '$resolved'") {
            override fun test(item: JavaClass): Boolean {
                val pkg = item.packageName.lowercase()
                val simple = item.name.substringAfterLast('.').lowercase()
                val annotations = item.annotations.map { ann ->
                    ann.type.name.substringAfterLast('.').lowercase()
                }

                return when (resolved) {
                    ClassType.CONTROLLER ->
                        pkg.contains("controller") ||
                                pkg.contains("web") ||
                                pkg.contains("api") ||
                                simple.endsWith("controller") ||
                                annotations.any { it == "controller" || it == "restcontroller" }

                    ClassType.SERVICE ->
                        pkg.contains("service") ||
                                pkg.contains("business") ||
                                simple.endsWith("service") ||
                                simple.endsWith("usecase") ||
                                simple.endsWith("interactor") ||
                                annotations.any { it == "service" }

                    ClassType.REPOSITORY ->
                        pkg.contains("repository") ||
                                pkg.contains("dao") ||
                                pkg.contains("data") ||
                                simple.endsWith("repository") ||
                                simple.endsWith("dao") ||
                                annotations.any { it == "repository" }

                    ClassType.ENTITY ->
                        pkg.contains("entity") ||
                                pkg.contains("model") ||
                                pkg.contains("domain") ||
                                simple.endsWith("entity") ||
                                annotations.any { it == "entity" || it == "table" }

                    ClassType.DTO ->
                        pkg.contains("dto") ||
                                pkg.contains("vo") ||
                                pkg.contains("request") ||
                                pkg.contains("response") ||
                                simple.endsWith("dto") ||
                                simple.endsWith("request") ||
                                simple.endsWith("response")

                    ClassType.OTHER -> true
                }
            }
        }
    }

    private fun wildcardToRegex(pattern: String): String {
        val subpackageWildcard = "__SUBPKG_WILDCARD__"
        return pattern
            .replace("**", ".*")
            .replace("..*", subpackageWildcard)
            .replace("*", "[^.]*")
            .replace(".", "\\.")
            .replace(subpackageWildcard, "(\\..*)?")
    }
}