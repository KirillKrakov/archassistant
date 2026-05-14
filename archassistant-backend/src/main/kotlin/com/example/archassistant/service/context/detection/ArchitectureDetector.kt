package com.example.archassistant.service.context.detection

import com.example.archassistant.model.*
import com.example.archassistant.model.context.ProjectProfile
import com.example.archassistant.model.context.ProjectProfileDetection
import org.springframework.stereotype.Service

@Service
class ArchitectureDetector {

    fun detect(structure: ProjectStructure): ProjectProfileDetection {
        val index = PackageScopeIndex.from(structure)

        val scores = mapOf(
            ProjectProfile.MVVM to scoreMvvm(index),
            ProjectProfile.HEXAGONAL to scoreHexagonal(index),
            ProjectProfile.CLEAN to scoreClean(index),
            ProjectProfile.MODULAR to scoreModular(index),
            ProjectProfile.SPRING_FEATURED to scoreSpringFeatured(index),
            ProjectProfile.SPRING_LAYERED to scoreSpringLayered(index)
        )

        val primary = choosePrimaryProfile(index)
        val sorted = scores.entries.sortedWith(
            compareByDescending<Map.Entry<ProjectProfile, Double>> { it.value }
                .thenByDescending { priority(it.key) }
        )

        val topScore = scores[primary] ?: 0.0
        val secondScore = sorted.firstOrNull { it.key != primary }?.value ?: 0.0
        val total = scores.values.sum().coerceAtLeast(1.0)

        val confidence = (topScore / total).coerceIn(0.0, 1.0)
        val confident = when (primary) {
            ProjectProfile.HEXAGONAL -> topScore >= 8.0 && (topScore - secondScore) >= 1.0
            ProjectProfile.CLEAN -> topScore >= 7.5 && (topScore - secondScore) >= 1.0
            ProjectProfile.SPRING_FEATURED -> topScore >= 8.0 && (topScore - secondScore) >= 1.0
            ProjectProfile.SPRING_LAYERED -> topScore >= 6.0 && (topScore - secondScore) >= 1.0
            ProjectProfile.MODULAR -> topScore >= 7.0 && (topScore - secondScore) >= 1.0
            ProjectProfile.MVVM -> topScore >= 6.0 && (topScore - secondScore) >= 1.0
            ProjectProfile.UNKNOWN -> false
        }

        return ProjectProfileDetection(
            primaryProfile = primary,
            confidence = confidence,
            scores = scores,
            reasons = buildReasons(primary, index),
            candidateProfiles = sorted.take(3).map { it.key },
            isConfident = confident
        )
    }

    private fun choosePrimaryProfile(
        index: PackageScopeIndex
    ): ProjectProfile {
        val hasHexagonalSignals =
            index.hasAnyPackageKeyword("port", "ports", "spi", "contract", "gateway", "adapter", "adapters") &&
                    index.hasAnyPackageKeyword("domain", "application")

        val hasCleanSignals =
            index.hasAnyPackageKeyword("domain", "core", "application", "infrastructure", "interface", "presentation")

        val springFeatureRoots = index.springFeatureRoots()
        val hasSpringFeatureSignals = index.isSpringLike() && springFeatureRoots.size >= 2

        val hasSpringLayeredSignals = index.isSpringLike()

        val hasModularSignals =
            index.hasAnyPackageKeyword("common", "shared", "feature", "module", "api", "impl") &&
                    index.featureRoots.size >= 2

        return when {
            hasHexagonalSignals -> ProjectProfile.HEXAGONAL
            hasCleanSignals -> ProjectProfile.CLEAN
            hasSpringFeatureSignals -> ProjectProfile.SPRING_FEATURED
            hasSpringLayeredSignals -> ProjectProfile.SPRING_LAYERED
            hasModularSignals -> ProjectProfile.MODULAR
            else -> ProjectProfile.UNKNOWN
        }
    }

    private fun scoreSpringLayered(index: PackageScopeIndex): Double {
        if (!index.isSpringLike()) return 0.0

        val controller = index.countLayer(LayerType.CONTROLLER)
        val service = index.countLayer(LayerType.SERVICE)
        val repository = index.countLayer(LayerType.REPOSITORY)

        return 7.0 +
                (controller * 1.2) +
                (service * 1.2) +
                (repository * 1.2) +
                if (index.hasAnyPackageKeyword("controller", "service", "repository")) 1.5 else 0.0
    }

    private fun scoreSpringFeatured(index: PackageScopeIndex): Double {
        val roots = index.springFeatureRoots()
        if (!index.isSpringLike() || roots.isEmpty()) return 0.0

        return 8.5 +
                (roots.size * 1.5) +
                if (roots.size >= 2) 2.0 else 0.0
    }

    private fun scoreClean(index: PackageScopeIndex): Double {
        val hasCleanPackages = index.hasAnyPackageKeyword("domain", "core", "application", "infrastructure", "interface", "presentation", "entities", "entity")
        if (!hasCleanPackages) return 0.0

        val domain = index.hasAnyPackageKeyword("domain", "core", "entity", "entities")
        val application = index.hasAnyPackageKeyword("application", "usecase", "use-case", "interactor")
        val infra = index.hasAnyPackageKeyword("infrastructure", "persistence", "adapter", "gateway")
        val iface = index.hasAnyPackageKeyword("interface", "presentation", "web", "rest", "api")

        return 8.8 +
                if (domain) 1.5 else 0.0 +
                        if (application) 1.5 else 0.0 +
                                if (infra) 1.5 else 0.0 +
                                        if (iface) 1.0 else 0.0
    }

    private fun scoreHexagonal(index: PackageScopeIndex): Double {
        val portLike = index.hasAnyPackageKeyword("port", "ports", "spi", "contract", "gateway")
        val adapterLike = index.hasAnyPackageKeyword("adapter", "adapters", "impl", "implementation")
        val hasCore = index.hasAnyPackageKeyword("domain", "core", "application")

        if (!hasCore) return 0.0

        return 9.2 +
                if (portLike) 2.0 else 0.0 +
                        if (adapterLike) 2.0 else 0.0 +
                                if (index.hasAnyPackageKeyword("domain", "application")) 1.0 else 0.0
    }

    private fun scoreModular(index: PackageScopeIndex): Double {
        val roots = index.featureRoots
        val modularSignals = index.hasAnyPackageKeyword("api", "impl", "common", "shared", "module", "feature")
        if (!modularSignals || roots.size < 2) return 0.0

        return 8.3 +
                (roots.size * 1.0) +
                if (index.hasAnyPackageKeyword("api")) 0.7 else 0.0 +
                        if (index.hasAnyPackageKeyword("impl")) 0.7 else 0.0 +
                                if (index.hasAnyPackageKeyword("common", "shared")) 0.7 else 0.0
    }

    private fun scoreMvvm(index: PackageScopeIndex): Double {
        val mvvmSignals = index.hasAnyPackageKeyword("viewmodel", "view", "ui", "screen", "fragment", "activity", "compose")
        if (!mvvmSignals) return 0.0

        return 8.6 +
                if (index.hasAnyPackageKeyword("viewmodel")) 1.5 else 0.0 +
                        if (index.hasAnyPackageKeyword("view", "ui", "screen", "fragment", "activity")) 1.2 else 0.0
    }

    private fun buildReasons(profile: ProjectProfile, index: PackageScopeIndex): List<String> {
        return when (profile) {
            ProjectProfile.SPRING_LAYERED -> listOf(
                "Found controller/service/repository classes",
                "Packages look Spring-style rather than feature-first"
            )

            ProjectProfile.SPRING_FEATURED -> listOf(
                "Found Spring-style layers",
                "Multiple non-technical feature roots detected: ${index.featureRoots.joinToString(", ")}"
            )

            ProjectProfile.CLEAN -> listOf(
                "Found domain/application/infrastructure/interface packages"
            )

            ProjectProfile.HEXAGONAL -> listOf(
                "Found port/adapter style packages",
                "Found core domain/application packages"
            )

            ProjectProfile.MODULAR -> listOf(
                "Found multiple feature roots: ${index.featureRoots.joinToString(", ")}",
                "Found api/impl/common/shared/module signals"
            )

            ProjectProfile.MVVM -> listOf(
                "Found view/viewmodel/UI signals"
            )

            ProjectProfile.UNKNOWN -> listOf("No confident profile evidence")
        }
    }

    private fun priority(profile: ProjectProfile): Int {
        return when (profile) {
            ProjectProfile.HEXAGONAL -> 6
            ProjectProfile.CLEAN -> 5
            ProjectProfile.MODULAR -> 4
            ProjectProfile.SPRING_FEATURED -> 3
            ProjectProfile.SPRING_LAYERED -> 2
            ProjectProfile.MVVM -> 1
            ProjectProfile.UNKNOWN -> 0
        }
    }
}