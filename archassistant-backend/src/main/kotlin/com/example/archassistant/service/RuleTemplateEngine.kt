package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.service.templates.*
import com.example.archassistant.util.PackagePatternBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RuleTemplateEngine(
    private val architectureDetector: ArchitectureDetector,
    private val templates: List<RuleTemplate> = loadDefaultTemplates()
) {

    private val logger = LoggerFactory.getLogger(RuleTemplateEngine::class.java)

    fun suggestRules(structure: ProjectStructure): List<ArchitecturalRule> {
        logger.debug("Generating rule suggestions for project: ${structure.projectId}")

        val detection = structure.detection ?: architectureDetector.detect(structure)
        logger.debug(
            "Detected architecture pattern: {}, confidence: {}",
            detection.primaryPattern,
            detection.confidence
        )

        val context = buildTemplateContext(structure, detection)
        val baselineIds = SpringBaselineRules.all().map { it.id }.toSet()

        val activePatterns = if (detection.isConfident) {
            listOf(detection.primaryPattern)
        } else {
            detection.candidatePatterns.filterNot { it == ArchitecturePattern.UNKNOWN }
                .ifEmpty { listOf(detection.primaryPattern) }
        }

        val patternSuggestions = templates
            .filter { it.id !in baselineIds }
            .filter { template -> template.applicablePatterns.any { it in activePatterns } }
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

        val springFeatureSuggestions =
            if (structure.isSpringLike() && activePatterns.contains(ArchitecturePattern.LAYERED)) {
                SpringFeatureRules.generate(structure, context.basePackage)
            } else {
                emptyList()
            }

        val fallbackSuggestions =
            if (!detection.isConfident || detection.primaryPattern == ArchitecturePattern.UNKNOWN) {
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

        val suggestions = (patternSuggestions + springFeatureSuggestions + fallbackSuggestions)
            .distinctBy { semanticKey(it) }
            .sortedWith(
                compareByDescending<ArchitecturalRule> { severityRank(it.severity) }
                    .thenByDescending { specificityScore(it) }
                    .thenByDescending { it.weight }
                    .thenBy { it.id }
            )

        logger.debug("Generated ${suggestions.size} rule suggestions")
        return suggestions
    }

    private fun buildTemplateContext(
        structure: ProjectStructure,
        detection: ArchitectureDetectionResult
    ): TemplateContext {
        val basePackage = findBasePackage(structure.packages)

        val effectiveMap = structure.effectiveLayerMap()
        val classesByLayer = LayerType.entries.associateWith { layer ->
            effectiveMap[layer].orEmpty()
        }

        val layers = LayerType.entries.associateWith { layer ->
            buildPackageInfos(effectiveMap[layer].orEmpty())
        }

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

    private fun findBasePackage(packages: List<String>): String {
        val normalized = packages
            .map { it.trim().trim('.') }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalized.isEmpty()) return ""
        if (normalized.size == 1) return normalized.first().substringBeforeLast('.', "")

        return PackagePatternBuilder.commonPackagePrefix(normalized)
    }

    private fun semanticKey(rule: ArchitecturalRule): String {
        return listOf(
            rule.type.name,
            rule.constraint.name,
            rule.fromSelectorMode.name,
            rule.toSelectorMode.name,
            rule.fromPackage,
            rule.toPackage.orEmpty(),
            rule.toPackages?.joinToString(",").orEmpty(),
            rule.pattern.orEmpty(),
            rule.annotation.orEmpty(),
            rule.fromClassType?.name.orEmpty(),
            rule.toClassType?.name.orEmpty(),
            rule.fromLayerType?.name.orEmpty(),
            rule.toLayerType?.name.orEmpty()
        ).joinToString("|")
    }

    private fun severityRank(severity: Severity): Int {
        return when (severity) {
            Severity.CRITICAL -> 4
            Severity.ERROR -> 3
            Severity.WARNING -> 2
            Severity.INFO -> 1
        }
    }

    private fun specificityScore(rule: ArchitecturalRule): Int {
        var score = 0
        if (rule.fromClassType != null) score += 4
        if (rule.toClassType != null) score += 4
        if (rule.fromLayerType != null) score += 3
        if (rule.toLayerType != null) score += 3
        if (rule.annotation != null) score += 2
        if (rule.pattern != null) score += 2
        if (rule.fromPackage.count { it == '.' } > 2) score += 1
        if (rule.toPackage?.count { it == '.' } ?: 0 > 2) score += 1
        return score
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