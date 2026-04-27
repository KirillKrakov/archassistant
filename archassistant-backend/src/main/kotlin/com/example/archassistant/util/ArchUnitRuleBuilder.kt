package com.example.archassistant.util

import com.example.archassistant.model.*
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition

/**
 * Фабрика для создания ArchUnit правил из декларативных моделей
 */
object ArchUnitRuleBuilder {

    /**
     * Создание ArchRule из ArchitecturalRule
     * @return ArchRule или null, если тип правила не поддерживается
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
     * Правило: классы из fromPackage не должны/должны зависеть от toPackage
     */
    private fun buildDependencyRule(rule: ArchitecturalRule): ArchRule? {
        val fromPattern = rule.fromPackage

        // Определяем целевые пакеты — приоритет toPackages, затем toPackage
        val targetPackages = when {
            !rule.toPackages.isNullOrEmpty() -> rule.toPackages
            rule.toPackage != null -> listOf(rule.toPackage)
            else -> emptyList()
        }

        if (targetPackages.isEmpty()) {
            throw UnsupportedOperationException("Dependency rule must have toPackage or toPackages")
        }

        // Базовое правило в зависимости от типа ограничения
        val baseRule = when (rule.constraint) {
            ConstraintType.NO_DEPENDENCY ->
                ArchRuleDefinition.noClasses()
                    .that().resideInAPackage(fromPattern)
                    .should().dependOnClassesThat()
            ConstraintType.MUST_DEPEND ->
                ArchRuleDefinition.classes()
                    .that().resideInAPackage(fromPattern)
                    .should().dependOnClassesThat()
            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for DEPENDENCY rules")
        }

        // Используем resideInAnyPackage для нескольких целевых пакетов
        val targetArray = targetPackages.toTypedArray()
        val finalRule = if (targetPackages.size == 1) {
            baseRule.resideInAPackage(targetPackages.first())
        } else {
            baseRule.resideInAnyPackage(*targetArray)
        }

        return finalRule.because(rule.description ?: rule.name)
    }

    /**
     * Правило: классы в fromPackage должны/не должны иметь определённый суффикс/префикс
     */
    private fun buildNamingRule(rule: ArchitecturalRule): ArchRule {
        val packagePattern = rule.fromPackage
        val pattern = rule.pattern ?: ""

        return when (rule.constraint) {
            ConstraintType.NAMING_SUFFIX ->
                ArchRuleDefinition.classes()
                    .that().resideInAPackage(packagePattern)
                    .should().haveSimpleNameEndingWith(pattern)
                    .because(rule.description ?: rule.name)

            ConstraintType.NAMING_PREFIX ->
                ArchRuleDefinition.classes()
                    .that().resideInAPackage(packagePattern)
                    .should().haveSimpleNameStartingWith(pattern)
                    .because(rule.description ?: rule.name)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for NAMING_CONVENTION rules")
        }
    }

    /**
     * Правило: классы в fromPackage должны/не должны иметь определённую аннотацию
     */
    private fun buildAnnotationRule(rule: ArchitecturalRule): ArchRule {
        val packagePattern = rule.fromPackage
        val annotation = rule.annotation ?: ""

        return when (rule.constraint) {
            ConstraintType.HAS_ANNOTATION ->
                ArchRuleDefinition.classes()
                    .that().resideInAPackage(packagePattern)
                    .should().beAnnotatedWith(annotation)
                    .because(rule.description ?: rule.name)

            ConstraintType.NO_ANNOTATION ->
                ArchRuleDefinition.noClasses()
                    .that().resideInAPackage(packagePattern)
                    .should().beAnnotatedWith(annotation)
                    .because(rule.description ?: rule.name)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for ANNOTATION_CHECK rules")
        }
    }

    /**
     * Правило: изоляция слоя (упрощённо — запрет зависимостей на внешние пакеты)
     */
    private fun buildLayerIsolationRule(rule: ArchitecturalRule): ArchRule {
        // Layer isolation — это частный случай dependency rule
        return buildDependencyRule(rule)
            ?: throw IllegalStateException("Failed to build layer isolation rule")
    }
}