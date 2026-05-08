package com.example.archassistant.model

import java.time.LocalDateTime

data class ProjectStructure(
    val projectId: String,
    val architecturePattern: ArchitecturePattern? = null,
    val detection: ProjectProfileDetection? = null,
    val packages: List<String> = emptyList(),
    val classes: List<ClassInfo> = emptyList(),
    val layers: LayerStructure = LayerStructure(),
    val layerMap: Map<LayerType, List<ClassInfo>> = emptyMap(),
    val annotations: Map<String, Int> = emptyMap(),
    val dependencies: List<Dependency> = emptyList(),
    val namingConventions: NamingConventions = NamingConventions(),
    val violations: List<Violation> = emptyList(),
    val scannedAt: String = LocalDateTime.now().toString()
) {
    fun effectiveLayerMap(): Map<LayerType, List<ClassInfo>> {
        return if (layerMap.isNotEmpty()) {
            LayerType.entries.associateWith { layerMap[it].orEmpty() }
        } else {
            mapOf(
                LayerType.CONTROLLER to layers.controllers,
                LayerType.SERVICE to layers.services,
                LayerType.REPOSITORY to layers.repositories,
                LayerType.ENTITY to layers.entities,
                LayerType.DTO to layers.dtos,
                LayerType.DOMAIN to emptyList(),
                LayerType.APPLICATION to emptyList(),
                LayerType.INFRASTRUCTURE to emptyList(),
                LayerType.INTERFACE to emptyList(),
                LayerType.VIEW to emptyList(),
                LayerType.VIEWMODEL to emptyList(),
                LayerType.PORT to emptyList(),
                LayerType.ADAPTER to emptyList(),
                LayerType.API to emptyList(),
                LayerType.IMPL to emptyList(),
                LayerType.FEATURE to emptyList(),
                LayerType.COMMON to emptyList(),
                LayerType.OTHER to layers.other
            )
        }
    }

    fun getClassesForLayer(layerType: LayerType): List<ClassInfo> = effectiveLayerMap()[layerType].orEmpty()

    fun getPackagesForLayer(layerType: LayerType): List<String> =
        getClassesForLayer(layerType).map { it.packageName }.distinct()

    fun usesAnnotation(annotation: String): Boolean {
        val normalized = annotation.removePrefix("@")
        return annotations.entries.any { (key, value) ->
            value > 0 && key.removePrefix("@").equals(normalized, ignoreCase = true)
        }
    }

    fun getExpectedSuffix(layerType: LayerType): String {
        return when (layerType) {
            LayerType.CONTROLLER -> "Controller"
            LayerType.SERVICE -> "Service"
            LayerType.REPOSITORY -> "Repository"
            LayerType.ENTITY -> ""
            LayerType.DTO -> "Dto"
            else -> ""
        }
    }

    fun isSpringLike(): Boolean {
        return getClassesForLayer(LayerType.CONTROLLER).isNotEmpty() ||
                getClassesForLayer(LayerType.SERVICE).isNotEmpty() ||
                getClassesForLayer(LayerType.REPOSITORY).isNotEmpty() ||
                annotations.keys.any { key ->
                    val k = key.removePrefix("@").lowercase()
                    k == "controller" || k == "restcontroller" || k == "service" || k == "repository"
                }
    }

    fun featureRoots(basePackage: String): List<String> {
        val normalizedBase = basePackage.trim().trim('.')
        if (normalizedBase.isBlank()) return emptyList()

        val technicalRoots = setOf(
            "controller", "service", "repository", "entity", "dto", "model",
            "domain", "application", "infrastructure", "interface", "view",
            "viewmodel", "port", "adapter", "api", "impl", "common", "shared",
            "config", "util", "web", "rest"
        )

        return packages.mapNotNull { pkg ->
            val rel = pkg.removePrefix(normalizedBase).trim('.')
            val root = rel.split('.').firstOrNull().orEmpty()
            root.takeIf { it.isNotBlank() && it.lowercase() !in technicalRoots }
        }.distinct()
    }
}

data class LayerStructure(
    val controllers: List<ClassInfo> = emptyList(),
    val services: List<ClassInfo> = emptyList(),
    val repositories: List<ClassInfo> = emptyList(),
    val entities: List<ClassInfo> = emptyList(),
    val dtos: List<ClassInfo> = emptyList(),
    val other: List<ClassInfo> = emptyList()
)

enum class ClassKind {
    CLASS,
    INTERFACE,
    ENUM,
    ANNOTATION,
    OTHER
}

enum class ClassOrigin {
    BASE,
    OVERLAY
}

data class FieldInfo(
    val name: String,
    val type: String,
    val modifiers: List<String> = emptyList()
)

data class ConstructorInfo(
    val parameters: List<String> = emptyList(),
    val modifiers: List<String> = emptyList()
)

data class ClassInfo(
    val fullName: String,
    val simpleName: String,
    val packageName: String,
    val kind: ClassKind = ClassKind.CLASS,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val annotations: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val modifiers: List<String> = emptyList(),
    val fields: List<FieldInfo> = emptyList(),
    val constructors: List<ConstructorInfo> = emptyList(),
    val publicMethods: List<String> = emptyList(),
    val origin: ClassOrigin = ClassOrigin.BASE
)

enum class ClassType {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    ENTITY,
    DTO,
    OTHER
}

enum class LayerType {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    ENTITY,
    DTO,
    DOMAIN,
    APPLICATION,
    INFRASTRUCTURE,
    INTERFACE,
    VIEW,
    VIEWMODEL,
    PORT,
    ADAPTER,
    API,
    IMPL,
    FEATURE,
    COMMON,
    OTHER
}

data class Dependency(
    val from: String,
    val to: String,
    val type: DependencyType = DependencyType.IMPORT
)

enum class DependencyType {
    IMPORT,
    FIELD,
    METHOD_PARAM,
    RETURN_TYPE,
    INHERITANCE
}

data class NamingConventions(
    val serviceSuffix: String = "Service",
    val repositorySuffix: String = "Repository",
    val controllerSuffix: String = "Controller",
    val dtoSuffix: String = "Dto",
    val packagePrefix: String = "",
    val compliance: Map<String, Double> = emptyMap()
)