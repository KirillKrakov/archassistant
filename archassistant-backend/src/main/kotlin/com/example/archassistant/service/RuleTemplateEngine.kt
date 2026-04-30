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
    private val architectureDetector: ArchitectureDetector,
    private val templates: List<RuleTemplate> = loadDefaultTemplates()
) {

    private val logger = LoggerFactory.getLogger(RuleTemplateEngine::class.java)

    /**
     * Генерация предложений правил для проекта
     */
    fun suggestRules(structure: ProjectStructure): List<ArchitecturalRule> {
        logger.debug("Generating rule suggestions for project: ${structure.projectId}")

        val detection = architectureDetector.detect(structure)
        logger.debug(
            "Detected architecture pattern: {}, confidence: {}",
            detection.primaryPattern,
            detection.confidence
        )

        val context = buildTemplateContext(structure, detection)

        val baselineIds = SpringBaselineRules.all().map { it.id }.toSet()

        val primarySuggestions = templates
            .filter { it.id !in baselineIds }
            .filter { it.applicablePatterns.contains(detection.primaryPattern) }
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

        val fallbackSuggestions =
            if (detection.primaryPattern == ArchitecturePattern.UNKNOWN || detection.confidence < 0.65) {
                templates
                    .filter { it.id in baselineIds }
                    .filter { it.isApplicable(context) }
                    .sortedByDescending { it.priority }
                    .flatMap { template ->
                        try {
                            template.generate(context)
                        } catch (e: Exception) {
                            logger.warn("Failed to generate fallback rules from template ${template.id}: ${e.message}")
                            emptyList()
                        }
                    }
            } else {
                emptyList()
            }

        val suggestions = (primarySuggestions + fallbackSuggestions)
            .distinctBy { it.id }
            .sortedByDescending { it.weight }
            .sortedByDescending { it.severity.ordinal }

        logger.debug("Generated ${suggestions.size} rule suggestions")
        return suggestions
    }

    /**
     * Построение контекста для применения шаблонов
     */
    private fun buildTemplateContext(
        structure: ProjectStructure,
        detection: ArchitectureDetectionResult
    ): TemplateContext {
        val basePackage = findBasePackage(structure.packages)

        val controllerInfos = structure.layers.controllers
        val serviceInfos = structure.layers.services
        val repositoryInfos = structure.layers.repositories
        val entityInfos = structure.layers.entities
        val dtoInfos = structure.layers.dtos
        val otherInfos = structure.layers.other

        val classesByLayer = mapOf(
            LayerType.CONTROLLER to controllerInfos,
            LayerType.SERVICE to serviceInfos,
            LayerType.REPOSITORY to repositoryInfos,
            LayerType.ENTITY to entityInfos,
            LayerType.DTO to dtoInfos,
            LayerType.OTHER to otherInfos
        )

        val layers = mapOf(
            LayerType.CONTROLLER to buildPackageInfos(controllerInfos),
            LayerType.SERVICE to buildPackageInfos(serviceInfos),
            LayerType.REPOSITORY to buildPackageInfos(repositoryInfos),
            LayerType.ENTITY to buildPackageInfos(entityInfos),
            LayerType.DTO to buildPackageInfos(dtoInfos),
            LayerType.OTHER to buildPackageInfos(otherInfos)
        )

        val dependencies = structure.dependencies.map { dep ->
            DependencyInfo(
                fromPackage = dep.from.substringBeforeLast('.'),
                toPackage = dep.to.substringBeforeLast('.'),
                dependencyType = dep.type
            )
        }

        return TemplateContext(
            projectId = structure.projectId,
            basePackage = basePackage,
            architecturePattern = detection.primaryPattern,
            detection = detection,
            layers = layers,
            classesByLayer = classesByLayer,
            annotations = structure.annotations,
            namingConventions = structure.namingConventions,
            dependencies = dependencies
        )
    }

    private fun buildPackageInfos(classes: List<ClassInfo>): List<PackageInfo> {
        return classes
            .groupBy { it.packageName }
            .map { (packageName, infos) ->
                PackageInfo(
                    packageName = packageName,
                    classCount = infos.size,
                    annotations = infos.flatMap { it.annotations }.distinct(),
                    dependencies = infos.flatMap { it.dependencies }.distinct()
                )
            }
            .sortedBy { it.packageName }
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

    companion object {
        fun loadDefaultTemplates(): List<RuleTemplate> {
            return listOf(
                *LayeredArchitectureRules.all().toTypedArray(),
                *CleanArchitectureRules.all().toTypedArray(),
                *HexagonalRules.all().toTypedArray(),
                *ModularRules.all().toTypedArray(),
                *MvvmArchitectureRules.all().toTypedArray(),
                *SpringBaselineRules.all().toTypedArray()
            )
        }
    }
}