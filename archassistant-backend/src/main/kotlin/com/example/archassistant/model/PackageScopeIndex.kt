package com.example.archassistant.model

import com.example.archassistant.util.PackagePatternBuilder
import com.example.archassistant.util.ProjectLayerClassifier

data class PackageScope(
    val packageName: String,
    val root: String,
    val classes: List<ClassInfo>,
    val layerCounts: Map<LayerType, Int>,
    val dominantLayer: LayerType,
    val technical: Boolean
)

data class PackageScopeIndex(
    val basePackage: String,
    val packageScopes: List<PackageScope>,
    val scopesByRoot: Map<String, List<PackageScope>>,
    val globalLayerCounts: Map<LayerType, Int>,
    val featureRoots: List<String>
) {
    fun allPackages(): List<String> = packageScopes.map { it.packageName }.distinct()

    fun scopePattern(root: String): String {
        return if (basePackage.isBlank()) {
            "$root..*"
        } else {
            "$basePackage.$root..*"
        }
    }

    fun scopePatternsForRoots(vararg roots: String): List<String> {
        return roots
            .flatMap { root ->
                scopesByRoot[root].orEmpty().map { scopePattern(it.root) }
            }
            .distinct()
    }

    fun packagesContaining(vararg keywords: String): List<String> {
        val normalized = keywords.map { it.lowercase() }
        return allPackages().filter { pkg ->
            val p = pkg.lowercase()
            normalized.any { p.contains(it) }
        }
    }

    fun rootsContaining(vararg keywords: String): List<String> {
        val normalized = keywords.map { it.lowercase() }
        return scopesByRoot.keys.filter { root ->
            val r = root.lowercase()
            normalized.any { r.contains(it) }
        }
    }

    fun classesInRoot(root: String): List<ClassInfo> {
        return scopesByRoot[root].orEmpty().flatMap { it.classes }
    }

    fun hasAnyPackageKeyword(vararg keywords: String): Boolean {
        return packagesContaining(*keywords).isNotEmpty()
    }

    fun hasAnyLayer(vararg layers: LayerType): Boolean {
        return layers.any { countLayer(it) > 0 }
    }

    fun hasAnyClassType(vararg types: ClassType): Boolean {
        return types.any { type ->
            val layer = type.toLayerType() ?: return@any false
            countLayer(layer) > 0
        }
    }

    fun hasAnyClassTypeInRoot(root: String, vararg types: ClassType): Boolean {
        val classes = classesInRoot(root)
        return classes.any { info ->
            types.any { type -> ProjectLayerClassifier.matchesClassType(info, type) }
        }
    }

    fun countLayer(layer: LayerType): Int = globalLayerCounts[layer] ?: 0

    fun isSpringLike(): Boolean {
        return hasAnyClassType(ClassType.CONTROLLER, ClassType.SERVICE, ClassType.REPOSITORY)
    }

    fun springFeatureRoots(): List<String> {
        return featureRoots.filter { root ->
            hasAnyClassTypeInRoot(root, ClassType.CONTROLLER, ClassType.SERVICE, ClassType.REPOSITORY)
        }
    }

    fun packagesForRoot(root: String): List<String> {
        return scopesByRoot[root].orEmpty().map { it.packageName }.distinct()
    }

    companion object {
        fun from(structure: ProjectStructure): PackageScopeIndex {
            val base = PackagePatternBuilder.commonPackagePrefix(structure.packages)

            val scopes = structure.classes
                .groupBy { it.packageName }
                .map { (packageName, classes) ->
                    val root = rootOf(packageName, base)
                    val layerCounts = classes
                        .groupingBy { ProjectLayerClassifier.classify(it) }
                        .eachCount()

                    val dominantLayer = layerCounts
                        .maxByOrNull { it.value }
                        ?.key ?: LayerType.OTHER

                    PackageScope(
                        packageName = packageName,
                        root = root,
                        classes = classes,
                        layerCounts = layerCounts,
                        dominantLayer = dominantLayer,
                        technical = root.lowercase() in TECHNICAL_ROOTS
                    )
                }

            val scopesByRoot = scopes.groupBy { it.root }

            val globalLayerCounts = scopes
                .flatMap { scope -> scope.layerCounts.entries }
                .groupingBy { it.key }
                .fold(0) { acc, entry -> acc + entry.value }

            val featureRoots = scopesByRoot.keys
                .filter { root ->
                    root.isNotBlank() &&
                            root.lowercase() !in TECHNICAL_ROOTS
                }
                .filter { root ->
                    scopesByRoot[root].orEmpty().sumOf { it.classes.size } >= 1
                }
                .distinct()

            return PackageScopeIndex(
                basePackage = base,
                packageScopes = scopes,
                scopesByRoot = scopesByRoot,
                globalLayerCounts = globalLayerCounts,
                featureRoots = featureRoots
            )
        }

        private fun rootOf(packageName: String, basePackage: String): String {
            val relative = if (basePackage.isNotBlank() && packageName.startsWith("$basePackage.")) {
                packageName.removePrefix("$basePackage.").trim('.')
            } else {
                packageName.trim('.')
            }

            return relative
                .split('.')
                .firstOrNull()
                .orEmpty()
                .ifBlank { packageName.substringAfterLast('.') }
        }

        private fun ClassType.toLayerType(): LayerType? {
            return when (this) {
                ClassType.CONTROLLER -> LayerType.CONTROLLER
                ClassType.SERVICE -> LayerType.SERVICE
                ClassType.REPOSITORY -> LayerType.REPOSITORY
                ClassType.ENTITY -> LayerType.ENTITY
                ClassType.DTO -> LayerType.DTO
                ClassType.OTHER -> null
            }
        }

        private val TECHNICAL_ROOTS = setOf(
            "controller", "service", "repository", "entity", "dto", "model",
            "domain", "application", "infrastructure", "interface",
            "view", "viewmodel", "port", "ports", "adapter", "adapters",
            "api", "impl", "common", "shared", "feature",
            "config", "util", "web", "rest", "presentation"
        )
    }
}