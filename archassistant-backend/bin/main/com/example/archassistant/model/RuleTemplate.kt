package com.example.archassistant.model

import com.example.archassistant.util.PackagePatternBuilder

/**
 * Абстракция шаблона архитектурного правила
 * Шаблон описывает, как сгенерировать правило для конкретного архитектурного паттерна
 */
sealed interface RuleTemplate {
    val id: String
    val name: String
    val description: String
    val applicablePatterns: Set<ArchitecturePattern>

    fun isApplicable(context: TemplateContext): Boolean
    fun generate(context: TemplateContext): List<ArchitecturalRule>

    val priority: Int get() = 50
}

/**
 * Базовая реализация для зависимостей между слоями на уровне пакетов/классов/слоёв
 */
abstract class LayerDependencyTemplate(
    override val id: String,
    override val name: String,
    override val description: String,
    override val applicablePatterns: Set<ArchitecturePattern>,
    private val fromLayer: LayerType,
    private val toLayer: LayerType,
    private val constraint: ConstraintType,
    private val severity: Severity,
    private val weight: Double,
    override val priority: Int = 50
) : RuleTemplate {

    override fun isApplicable(context: TemplateContext): Boolean {
        val fromPackages = context.getPackagesForLayer(fromLayer)
        val toPackages = context.getPackagesForLayer(toLayer)
        return fromPackages.isNotEmpty() && toPackages.isNotEmpty()
    }

    override fun generate(context: TemplateContext): List<ArchitecturalRule> {
        if (!isApplicable(context)) return emptyList()

        val fromClassType = fromLayer.toClassType()
        val toClassType = toLayer.toClassType()
        val fromPackages = context.getPackagesForLayer(fromLayer)
        val toPackages = context.getPackagesForLayer(toLayer)

        return if (fromClassType != null && toClassType != null) {
            val basePackage = context.basePackage.takeIf { it.isNotBlank() } ?: "..*"
            listOf(
                ArchitecturalRule(
                    id = "${id}_${context.projectId}_${fromClassType.name}_${toClassType.name}",
                    name = name,
                    description = description,
                    type = RuleType.DEPENDENCY,
                    fromPackage = "$basePackage..*",
                    toPackage = "$basePackage..*",
                    constraint = constraint,
                    severity = severity,
                    weight = weight,
                    enabled = true,
                    suggested = true,
                    fromSelectorMode = SelectorMode.CLASS_TYPE,
                    toSelectorMode = SelectorMode.CLASS_TYPE,
                    fromClassType = fromClassType,
                    toClassType = toClassType
                )
            )
        } else {
            val fromPatterns = PackagePatternBuilder.buildWildcardPatterns(fromPackages)
            val toPatterns = PackagePatternBuilder.buildWildcardPatterns(toPackages)

            if (fromPatterns.isEmpty() || toPatterns.isEmpty()) return emptyList()

            fromPatterns.flatMap { fromPat ->
                toPatterns.map { toPat ->
                    ArchitecturalRule(
                        id = "${id}_${context.projectId}_${fromPat.hashCode()}_${toPat.hashCode()}",
                        name = name,
                        description = description,
                        type = RuleType.DEPENDENCY,
                        fromPackage = fromPat,
                        toPackage = toPat,
                        constraint = constraint,
                        severity = severity,
                        weight = weight,
                        enabled = true,
                        suggested = true,
                        fromSelectorMode = SelectorMode.LAYER,
                        toSelectorMode = SelectorMode.LAYER,
                        fromLayerType = fromLayer,
                        toLayerType = toLayer
                    )
                }
            }
        }
    }
}

/**
 * Базовая реализация для зависимостей между типами классов
 * Используется для fallback-правил Spring/Android и для mixed package structures
 */
abstract class ClassDependencyTemplate(
    override val id: String,
    override val name: String,
    override val description: String,
    override val applicablePatterns: Set<ArchitecturePattern>,
    private val fromLayer: LayerType,
    private val toLayer: LayerType,
    private val constraint: ConstraintType,
    private val severity: Severity,
    private val weight: Double,
    override val priority: Int = 50
) : RuleTemplate {

    override fun isApplicable(context: TemplateContext): Boolean {
        return context.getClassesForLayer(fromLayer).isNotEmpty() &&
                context.getClassesForLayer(toLayer).isNotEmpty()
    }

    override fun generate(context: TemplateContext): List<ArchitecturalRule> {
        if (!isApplicable(context)) return emptyList()

        val fromClassType = fromLayer.toClassType()
        val toClassType = toLayer.toClassType()
        val basePackage = context.basePackage.takeIf { it.isNotBlank() } ?: "..*"

        return if (fromClassType != null && toClassType != null) {
            listOf(
                ArchitecturalRule(
                    id = "${id}_${context.projectId}_${fromClassType.name}_${toClassType.name}",
                    name = name,
                    description = description,
                    type = RuleType.DEPENDENCY,
                    fromPackage = "$basePackage..*",
                    toPackage = "$basePackage..*",
                    constraint = constraint,
                    severity = severity,
                    weight = weight,
                    enabled = true,
                    suggested = true,
                    fromSelectorMode = SelectorMode.CLASS_TYPE,
                    toSelectorMode = SelectorMode.CLASS_TYPE,
                    fromClassType = fromClassType,
                    toClassType = toClassType
                )
            )
        } else {
            listOf(
                ArchitecturalRule(
                    id = "${id}_${context.projectId}_${fromLayer.name}_${toLayer.name}",
                    name = name,
                    description = description,
                    type = RuleType.DEPENDENCY,
                    fromPackage = "$basePackage..*",
                    toPackage = "$basePackage..*",
                    constraint = constraint,
                    severity = severity,
                    weight = weight,
                    enabled = true,
                    suggested = true,
                    fromSelectorMode = SelectorMode.LAYER,
                    toSelectorMode = SelectorMode.LAYER,
                    fromLayerType = fromLayer,
                    toLayerType = toLayer
                )
            )
        }
    }
}

/**
 * Шаблон для правил именования на уровне пакетов
 */
abstract class NamingConventionTemplate(
    override val id: String,
    override val name: String,
    override val description: String,
    override val applicablePatterns: Set<ArchitecturePattern>,
    private val targetLayer: LayerType,
    private val expectedSuffix: String,
    private val severity: Severity,
    private val weight: Double,
    override val priority: Int = 40
) : RuleTemplate {

    override fun isApplicable(context: TemplateContext): Boolean {
        return context.getPackagesForLayer(targetLayer).isNotEmpty()
    }

    override fun generate(context: TemplateContext): List<ArchitecturalRule> {
        if (!isApplicable(context)) return emptyList()
        if (expectedSuffix.isBlank()) return emptyList()

        val packages = context.getPackagesForLayer(targetLayer)
        val patterns = PackagePatternBuilder.buildWildcardPatterns(packages)

        return patterns.map { packagePattern ->
            ArchitecturalRule(
                id = "${id}_${context.projectId}_${packagePattern.hashCode()}",
                name = name,
                description = description,
                type = RuleType.NAMING_CONVENTION,
                fromPackage = packagePattern,
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = expectedSuffix,
                severity = severity,
                weight = weight,
                enabled = true,
                suggested = true,
                fromSelectorMode = if (targetLayer.toClassType() != null) SelectorMode.CLASS_TYPE else SelectorMode.LAYER,
                fromClassType = targetLayer.toClassType(),
                fromLayerType = targetLayer.takeIf { it.toClassType() == null }
            )
        }
    }
}

/**
 * Шаблон для правил именования на уровне классов
 */
abstract class ClassNamingConventionTemplate(
    override val id: String,
    override val name: String,
    override val description: String,
    override val applicablePatterns: Set<ArchitecturePattern>,
    private val targetLayer: LayerType,
    private val expectedSuffix: String,
    private val severity: Severity,
    private val weight: Double,
    override val priority: Int = 40
) : RuleTemplate {

    override fun isApplicable(context: TemplateContext): Boolean {
        return context.getClassesForLayer(targetLayer).isNotEmpty()
    }

    override fun generate(context: TemplateContext): List<ArchitecturalRule> {
        if (!isApplicable(context)) return emptyList()
        if (expectedSuffix.isBlank()) return emptyList()

        val basePackage = context.basePackage.takeIf { it.isNotBlank() } ?: "..*"
        val classType = targetLayer.toClassType()

        return listOf(
            ArchitecturalRule(
                id = "${id}_${context.projectId}_${targetLayer.name}_${expectedSuffix}",
                name = name,
                description = description,
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "$basePackage..*",
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = expectedSuffix,
                severity = severity,
                weight = weight,
                enabled = true,
                suggested = true,
                fromSelectorMode = if (classType != null) SelectorMode.CLASS_TYPE else SelectorMode.LAYER,
                fromClassType = classType,
                fromLayerType = if (classType == null) targetLayer else null
            )
        )
    }
}

private fun LayerType.toClassType(): ClassType? {
    return when (this) {
        LayerType.CONTROLLER -> ClassType.CONTROLLER
        LayerType.SERVICE -> ClassType.SERVICE
        LayerType.REPOSITORY -> ClassType.REPOSITORY
        LayerType.ENTITY -> ClassType.ENTITY
        LayerType.DTO -> ClassType.DTO
        else -> null
    }
}