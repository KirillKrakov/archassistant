package com.example.archassistant.model

import com.example.archassistant.util.PackagePatternBuilder

/**
 * Абстракция шаблона архитектурного правила
 * Шаблон описывает, как сгенерировать правило для конкретного архитектурного паттерна
 */
sealed interface RuleTemplate {
    /**
     * Уникальный идентификатор шаблона
     */
    val id: String

    /**
     * Человеко-читаемое название
     */
    val name: String

    /**
     * Описание того, что проверяет правило
     */
    val description: String

    /**
     * Для каких архитектурных паттернов применим шаблон
     */
    val applicablePatterns: Set<ArchitecturePattern>

    /**
     * Проверка, применим ли шаблон к данному контексту
     */
    fun isApplicable(context: TemplateContext): Boolean

    /**
     * Генерация конкретного правила из шаблона
     * @return список сгенерированных правил (может быть 0, если условия не выполнены)
     */
    fun generate(context: TemplateContext): List<ArchitecturalRule>

    /**
     * Приоритет шаблона (для сортировки предложений)
     * Более высокий приоритет = правило показывается раньше
     */
    val priority: Int get() = 50
}

/**
 * Базовая реализация для зависимостей между слоями
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

        val fromPackages = context.getPackagesForLayer(fromLayer)
        val toPackages = context.getPackagesForLayer(toLayer)

        // FIXED: используем утилиту для корректных паттернов
        val fromPatterns = PackagePatternBuilder.buildWildcardPatterns(fromPackages)
        val toPatterns = PackagePatternBuilder.buildWildcardPatterns(toPackages)

        // Генерируем правило для каждой комбинации паттернов
        // (декартово произведение — каждое from с каждым to)
        return fromPatterns.flatMap { fromPat ->
            toPatterns.map { toPat ->
                ArchitecturalRule(
                    // FIXED: уникальный ID для каждой комбинации
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
                    suggested = true
                )
            }
        }
    }
}

/**
 * Шаблон для правил именования
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

        // FIXED: пропускаем генерацию, если суффикс пустой (бессмысленное правило)
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
                suggested = true
            )
        }
    }
}