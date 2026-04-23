package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.service.templates.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Движок генерации предложений правил на основе шаблонов
 */
@Service
class RuleTemplateEngine(
    private val templates: List<RuleTemplate> = loadDefaultTemplates()
) {

    private val logger = LoggerFactory.getLogger(RuleTemplateEngine::class.java)

    /**
     * Генерация предложений правил для проекта
     * @param structure Структура проекта (из сканирования)
     * @return Список предложенных правил с метаданными
     */
    fun suggestRules(structure: ProjectStructure): List<ArchitecturalRule> {
        logger.debug("Generating rule suggestions for project: ${structure.projectId}")

        // Шаг 1: Определяем архитектурный паттерн
        val pattern = detectArchitecturePattern(structure)
        logger.debug("Detected architecture pattern: $pattern")

        // Шаг 2: Строим контекст для шаблонов
        val context = buildTemplateContext(structure, pattern)

        // Шаг 3: Применяем шаблоны
        val suggestedRules = templates
            .filter { it.applicablePatterns.contains(pattern) }
            .filter { it.isApplicable(context) }
            .sortedByDescending { it.priority }
            .flatMap { template ->
                try {
                    template.generate(context)
                } catch (e: Exception) {
                    logger.warn("Failed to generate rules from template ${template.id}: ${e.message}")
                    emptyList()
                }
            }

        logger.debug("Generated ${suggestedRules.size} rule suggestions")
        return suggestedRules
    }

    /**
     * Определение архитектурного паттерна по структуре проекта
     */
    private fun detectArchitecturePattern(structure: ProjectStructure): ArchitecturePattern {
        return ArchitecturePattern.fromLayers(
            structure.packages,
            structure.annotations
        )
    }

    /**
     * Построение контекста для применения шаблонов
     */
    private fun buildTemplateContext(
        structure: ProjectStructure,
        pattern: ArchitecturePattern
    ): TemplateContext {
        // Определяем base package (наиболее общий префикс)
        val basePackage = findBasePackage(structure.packages)

        // Группируем пакеты по типам слоёв
        val layers = structure.packages.associateWith { packageName ->
            determineLayerType(packageName, structure.annotations, pattern)
        }.entries.groupBy { it.value }
            .mapValues { (_, entries) ->
                entries.map { PackageInfo(
                    packageName = it.key,
                    classCount = 0, // Можно дополнить при сканировании
                    annotations = emptyList(),
                    dependencies = emptyList()
                ) }
            }

        return TemplateContext(
            projectId = structure.projectId,
            basePackage = basePackage,
            architecturePattern = pattern,
            layers = layers,
            annotations = structure.annotations,
            namingConventions = structure.namingConventions,
            dependencies = structure.dependencies.map {
                DependencyInfo(
                    fromPackage = it.from.substringBeforeLast('.'),
                    toPackage = it.to.substringBeforeLast('.'),
                    dependencyType = when (it.type) {
                        DependencyType.IMPORT -> DependencyType.IMPORT
                        DependencyType.FIELD -> DependencyType.FIELD
                        DependencyType.METHOD_PARAM -> DependencyType.METHOD_PARAM
                        DependencyType.RETURN_TYPE -> DependencyType.RETURN_TYPE
                        DependencyType.INHERITANCE -> DependencyType.INHERITANCE
                    }
                )
            }
        )
    }

    /**
     * Поиск базового пакета (общий префикс всех пакетов)
     */
    private fun findBasePackage(packages: List<String>): String {
        if (packages.isEmpty()) return ""
        if (packages.size == 1) return packages.first().substringBeforeLast('.', "")

        var base = packages.first()
        for (pkg in packages.drop(1)) {
            while (!pkg.startsWith(base) && base.isNotEmpty()) {
                base = base.substringBeforeLast('.', "")
            }
        }
        return base
    }

    /**
     * Определение типа слоя по имени пакета и аннотациям
     */
    private fun determineLayerType(
        packageName: String,
        annotations: Map<String, Int>,
        pattern: ArchitecturePattern
    ): LayerType {
        val name = packageName.lowercase()

        return when {
            // 1. Clean / Hexagonal специфичные (высший приоритет)
            name.contains("domain") -> LayerType.DOMAIN
            name.contains("application") -> LayerType.APPLICATION
            name.contains("infrastructure") -> LayerType.INFRASTRUCTURE
            name.contains("interface") || name.contains("presentation") -> LayerType.INTERFACE
            name.contains("port") -> LayerType.PORT
            name.contains("adapter") -> LayerType.ADAPTER

            // 2. MVVM специфичные
            name.contains("viewmodel") || name.contains("vm") -> LayerType.VIEWMODEL
            name.contains("view") || name.contains("fragment") || name.contains("activity") -> LayerType.VIEW

            // 3. Общие по имени пакета (контроллеры, сервисы, репозитории)
            name.contains("controller") || name.contains("web") || name.contains("api") -> LayerType.CONTROLLER
            name.contains("service") || name.contains("business") -> LayerType.SERVICE
            name.contains("repository") || name.contains("dao") || name.contains("data") -> LayerType.REPOSITORY
            name.contains("entity") || name.contains("model") || name.contains("domain") -> LayerType.ENTITY
            name.contains("dto") || name.contains("vo") || name.contains("request") -> LayerType.DTO

            // 4. Модульные
            name.contains("api") && pattern == ArchitecturePattern.MODULAR -> LayerType.API
            name.contains("impl") && pattern == ArchitecturePattern.MODULAR -> LayerType.IMPL

            // 5. По аннотациям (fallback)
            annotations.containsKey("@RestController") || annotations.containsKey("@Controller") -> LayerType.CONTROLLER
            annotations.containsKey("@Service") -> LayerType.SERVICE
            annotations.containsKey("@Repository") -> LayerType.REPOSITORY
            annotations.containsKey("@Entity") -> LayerType.ENTITY

            else -> LayerType.OTHER
        }
    }

    /**
     * Загрузка шаблонов по умолчанию
     * Можно расширять через конфигурацию или SPI
     */
    companion object {
        fun loadDefaultTemplates(): List<RuleTemplate> {
            return listOf(
                // Layered Architecture
                *LayeredArchitectureRules.all().toTypedArray(),

                // Clean Architecture / Hexagonal
                *CleanArchitectureRules.all().toTypedArray(),

                // Hexagonal
                *HexagonalRules.all().toTypedArray(),

                // Modular
                *ModularRules.all().toTypedArray(),

                // MVVM
                *MvvmArchitectureRules.all().toTypedArray()
            )
        }
    }
}