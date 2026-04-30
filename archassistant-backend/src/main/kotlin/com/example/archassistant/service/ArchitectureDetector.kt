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
                reasons = listOf("No strong architectural signals found")
            )
        }

        val total = scores.values.sum().coerceAtLeast(1.0)
        val confidence = (top.value / total).coerceIn(0.0, 1.0)

        return ArchitectureDetectionResult(
            primaryPattern = top.key,
            confidence = confidence,
            scores = scores,
            reasons = buildReasons(top.key, structure)
        )
    }

    private fun scorePattern(pattern: ArchitecturePattern, structure: ProjectStructure): Double {
        val packageNames = structure.packages.map { it.lowercase() }
        val annotationNames = structure.annotations.keys.map { it.removePrefix("@").lowercase() }

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

        fun classCount(type: ClassType): Int = when (type) {
            ClassType.CONTROLLER -> structure.layers.controllers.size
            ClassType.SERVICE -> structure.layers.services.size
            ClassType.REPOSITORY -> structure.layers.repositories.size
            ClassType.ENTITY -> structure.layers.entities.size
            ClassType.DTO -> structure.layers.dtos.size
            ClassType.OTHER -> structure.layers.other.size
        }

        val pkgScore = packageHits(pattern.keyLayers) * 2.0
        val annScore = annotationHits(pattern.typicalAnnotations) * 1.5

        val classScore = when (pattern) {
            ArchitecturePattern.LAYERED -> {
                classCount(ClassType.CONTROLLER) +
                        classCount(ClassType.SERVICE) +
                        classCount(ClassType.REPOSITORY)
            }

            ArchitecturePattern.CLEAN_ARCHITECTURE -> {
                packageHits(listOf("domain", "application", "infrastructure", "interface")) +
                        classCount(ClassType.ENTITY)
            }

            ArchitecturePattern.HEXAGONAL -> {
                packageHits(listOf("domain", "application", "port", "adapter")) +
                        classCount(ClassType.ENTITY)
            }

            ArchitecturePattern.MVVM -> {
                packageHits(listOf("viewmodel", "view", "fragment", "activity")) +
                        annotationHits(listOf("@ViewModel", "@LiveData"))
            }

            ArchitecturePattern.MODULAR -> {
                packageHits(listOf("api", "impl", "common", "feature"))
            }

            ArchitecturePattern.UNKNOWN -> 0
        }.toDouble()

        val dependencyBonus = when (pattern) {
            ArchitecturePattern.LAYERED -> {
                val controllers = structure.layers.controllers.map { it.fullName }.toSet()
                val services = structure.layers.services.map { it.fullName }.toSet()
                val repositories = structure.layers.repositories.map { it.fullName }.toSet()

                val hasControllerToService = structure.dependencies.any { dep ->
                    dep.from in controllers && dep.to in services
                }
                val hasServiceToRepository = structure.dependencies.any { dep ->
                    dep.from in services && dep.to in repositories
                }

                (if (hasControllerToService) 1.0 else 0.0) + (if (hasServiceToRepository) 1.0 else 0.0)
            }

            else -> 0.0
        }

        return pkgScore + annScore + classScore + dependencyBonus
    }

    private fun buildReasons(pattern: ArchitecturePattern, structure: ProjectStructure): List<String> {
        val reasons = mutableListOf<String>()

        when (pattern) {
            ArchitecturePattern.LAYERED -> {
                if (structure.layers.controllers.isNotEmpty()) {
                    reasons += "Found controller classes"
                }
                if (structure.layers.services.isNotEmpty()) {
                    reasons += "Found service classes"
                }
                if (structure.layers.repositories.isNotEmpty()) {
                    reasons += "Found repository classes"
                }
            }

            ArchitecturePattern.CLEAN_ARCHITECTURE -> {
                if (structure.packages.any { it.contains("domain", ignoreCase = true) }) {
                    reasons += "Found domain packages"
                }
                if (structure.packages.any { it.contains("application", ignoreCase = true) }) {
                    reasons += "Found application packages"
                }
            }

            ArchitecturePattern.HEXAGONAL -> {
                if (structure.packages.any { it.contains("port", ignoreCase = true) }) {
                    reasons += "Found port packages"
                }
                if (structure.packages.any { it.contains("adapter", ignoreCase = true) }) {
                    reasons += "Found adapter packages"
                }
            }

            ArchitecturePattern.MVVM -> {
                if (structure.packages.any { it.contains("viewmodel", ignoreCase = true) }) {
                    reasons += "Found viewmodel packages"
                }
            }

            ArchitecturePattern.MODULAR -> {
                if (structure.packages.any { it.contains("feature", ignoreCase = true) }) {
                    reasons += "Found feature packages"
                }
            }

            ArchitecturePattern.UNKNOWN -> {
                reasons += "No confident pattern evidence"
            }
        }

        if (reasons.isEmpty()) {
            reasons += "Pattern detected by aggregated package/annotation/class signals"
        }

        return reasons
    }
}