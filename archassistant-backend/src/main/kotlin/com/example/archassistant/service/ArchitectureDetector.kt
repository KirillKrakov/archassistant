package com.example.archassistant.service

import com.example.archassistant.model.*
import org.springframework.stereotype.Service

@Service
class ArchitectureDetector {

    fun detect(structure: ProjectStructure): ArchitectureDetectionResult {
        val scores = ArchitecturePattern.entries
            .filter { it != ArchitecturePattern.UNKNOWN }
            .associateWith { pattern -> scorePattern(pattern, structure) }

        val sorted = scores.entries.sortedByDescending { it.value }
        val top = sorted.firstOrNull()

        if (top == null || top.value <= 0.0) {
            return ArchitectureDetectionResult(
                primaryPattern = ArchitecturePattern.UNKNOWN,
                confidence = 0.0,
                scores = scores,
                reasons = listOf("No strong architectural signals found"),
                candidatePatterns = emptyList(),
                isConfident = false
            )
        }

        val second = sorted.getOrNull(1)?.value ?: 0.0
        val total = scores.values.sum().coerceAtLeast(1.0)
        val confidence = (top.value / total).coerceIn(0.0, 1.0)
        val confident = top.value >= 4.0 && (top.value - second) >= 1.0

        return ArchitectureDetectionResult(
            primaryPattern = top.key,
            confidence = confidence,
            scores = scores,
            reasons = buildReasons(top.key, structure),
            candidatePatterns = sorted.take(3).map { it.key },
            isConfident = confident
        )
    }

    private fun scorePattern(pattern: ArchitecturePattern, structure: ProjectStructure): Double {
        val packageNames = structure.packages.map { it.lowercase() }.toSet()
        val annotationNames = structure.annotations.keys.map { it.removePrefix("@").lowercase() }.toSet()
        val layerMap = structure.effectiveLayerMap()

        fun packageHits(keywords: List<String>): Int {
            return keywords.count { keyword ->
                packageNames.any { it.contains(keyword.lowercase()) }
            }
        }

        fun annotationHits(keywords: List<String>): Int {
            return keywords.count { keyword ->
                val k = keyword.removePrefix("@").lowercase()
                annotationNames.any { it.contains(k) }
            }
        }

        fun layerCount(type: LayerType): Int = layerMap[type].orEmpty().size

        val pkgScore = packageHits(pattern.keyLayers) * when (pattern) {
            ArchitecturePattern.LAYERED -> 2.4
            ArchitecturePattern.CLEAN_ARCHITECTURE -> 2.2
            ArchitecturePattern.HEXAGONAL -> 2.2
            ArchitecturePattern.MVVM -> 2.0
            ArchitecturePattern.MODULAR -> 2.0
            ArchitecturePattern.UNKNOWN -> 1.0
        }

        val annScore = annotationHits(pattern.typicalAnnotations) * 1.3

        val classScore = when (pattern) {
            ArchitecturePattern.LAYERED -> layerCount(LayerType.CONTROLLER) +
                    layerCount(LayerType.SERVICE) +
                    layerCount(LayerType.REPOSITORY) +
                    layerCount(LayerType.ENTITY)

            ArchitecturePattern.CLEAN_ARCHITECTURE -> layerCount(LayerType.DOMAIN) +
                    layerCount(LayerType.APPLICATION) +
                    layerCount(LayerType.INFRASTRUCTURE) +
                    layerCount(LayerType.INTERFACE)

            ArchitecturePattern.HEXAGONAL -> layerCount(LayerType.DOMAIN) +
                    layerCount(LayerType.APPLICATION) +
                    layerCount(LayerType.PORT) +
                    layerCount(LayerType.ADAPTER)

            ArchitecturePattern.MVVM -> layerCount(LayerType.VIEW) +
                    layerCount(LayerType.VIEWMODEL)

            ArchitecturePattern.MODULAR -> layerCount(LayerType.API) +
                    layerCount(LayerType.IMPL) +
                    layerCount(LayerType.COMMON) +
                    layerCount(LayerType.FEATURE)

            ArchitecturePattern.UNKNOWN -> 0
        }.toDouble() * 1.1

        val dependencyBonus = when (pattern) {
            ArchitecturePattern.LAYERED -> {
                val controllers = layerMap[LayerType.CONTROLLER].orEmpty().map { it.fullName }.toSet()
                val services = layerMap[LayerType.SERVICE].orEmpty().map { it.fullName }.toSet()
                val repositories = layerMap[LayerType.REPOSITORY].orEmpty().map { it.fullName }.toSet()

                val hasControllerToService = structure.dependencies.any { dep ->
                    dep.from in controllers && dep.to in services
                }
                val hasServiceToRepository = structure.dependencies.any { dep ->
                    dep.from in services && dep.to in repositories
                }

                (if (hasControllerToService) 1.2 else 0.0) + (if (hasServiceToRepository) 1.2 else 0.0)
            }

            else -> 0.0
        }

        val extra = when (pattern) {
            ArchitecturePattern.LAYERED -> {
                if (
                    packageNames.any { it.contains("controller") } ||
                    packageNames.any { it.contains("service") } ||
                    packageNames.any { it.contains("repository") }
                ) 1.5 else 0.0
            }

            ArchitecturePattern.CLEAN_ARCHITECTURE -> {
                if (packageNames.any { it.contains("domain") } && packageNames.any { it.contains("application") }) 1.5 else 0.0
            }

            ArchitecturePattern.HEXAGONAL -> {
                if (packageNames.any { it.contains("port") } || packageNames.any { it.contains("adapter") }) 1.5 else 0.0
            }

            ArchitecturePattern.MVVM -> {
                if (packageNames.any { it.contains("viewmodel") } || packageNames.any { it.contains("view") }) 1.5 else 0.0
            }

            ArchitecturePattern.MODULAR -> {
                if (
                    packageNames.any { it.contains("feature") } ||
                    packageNames.any { it.contains("api") } ||
                    packageNames.any { it.contains("impl") }
                ) 1.0 else 0.0
            }

            ArchitecturePattern.UNKNOWN -> 0.0
        }

        return pkgScore + annScore + classScore + dependencyBonus + extra
    }

    private fun buildReasons(pattern: ArchitecturePattern, structure: ProjectStructure): List<String> {
        val reasons = mutableListOf<String>()
        val layerMap = structure.effectiveLayerMap()

        when (pattern) {
            ArchitecturePattern.LAYERED -> {
                if (layerMap[LayerType.CONTROLLER].orEmpty().isNotEmpty()) reasons += "Found controller classes"
                if (layerMap[LayerType.SERVICE].orEmpty().isNotEmpty()) reasons += "Found service classes"
                if (layerMap[LayerType.REPOSITORY].orEmpty().isNotEmpty()) reasons += "Found repository classes"
            }

            ArchitecturePattern.CLEAN_ARCHITECTURE -> {
                if (structure.packages.any { it.contains("domain", ignoreCase = true) }) reasons += "Found domain packages"
                if (structure.packages.any { it.contains("application", ignoreCase = true) }) reasons += "Found application packages"
            }

            ArchitecturePattern.HEXAGONAL -> {
                if (structure.packages.any { it.contains("port", ignoreCase = true) }) reasons += "Found port packages"
                if (structure.packages.any { it.contains("adapter", ignoreCase = true) }) reasons += "Found adapter packages"
            }

            ArchitecturePattern.MVVM -> {
                if (structure.packages.any { it.contains("viewmodel", ignoreCase = true) }) reasons += "Found viewmodel packages"
                if (structure.packages.any { it.contains("view", ignoreCase = true) || it.contains("ui", ignoreCase = true) }) reasons += "Found view/ui packages"
            }

            ArchitecturePattern.MODULAR -> {
                if (structure.packages.any { it.contains("feature", ignoreCase = true) }) reasons += "Found feature packages"
                if (structure.packages.any { it.contains("api", ignoreCase = true) }) reasons += "Found api packages"
            }

            ArchitecturePattern.UNKNOWN -> reasons += "No confident pattern evidence"
        }

        if (reasons.isEmpty()) reasons += "Pattern detected by aggregated package/annotation/class signals"
        return reasons
    }
}