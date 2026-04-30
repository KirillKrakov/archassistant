package com.example.archassistant.model

import java.time.LocalDateTime

/**
 * Структура проекта, извлечённая при сканировании
 * Используется для предложения правил и анализа архитектуры
 */
data class ProjectStructure(
    val projectId: String,
    val architecturePattern: ArchitecturePattern? = null,
    val detection: ArchitectureDetectionResult? = null,
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

    /**
     * Определение типа класса по аннотациям и расположению
     */
    fun determineClassType(className: String, packageName: String): ClassType {
        val pkg = packageName.lowercase()
        val simple = className.lowercase()

        return when {
            pkg.contains("controller") ||
                    simple.endsWith("controller") ->
                ClassType.CONTROLLER

            pkg.contains("service") ||
                    simple.endsWith("service") ||
                    simple.endsWith("usecase") ||
                    simple.endsWith("interactor") ->
                ClassType.SERVICE

            pkg.contains("repository") ||
                    pkg.contains("dao") ||
                    simple.endsWith("repository") ||
                    simple.endsWith("dao") ->
                ClassType.REPOSITORY

            pkg.contains("entity") ||
                    pkg.contains("model") ||
                    simple.endsWith("entity") ->
                ClassType.ENTITY

            pkg.contains("dto") ||
                    pkg.contains("vo") ||
                    pkg.contains("request") ||
                    pkg.contains("response") ||
                    simple.endsWith("dto") ->
                ClassType.DTO

            else -> ClassType.OTHER
        }
    }

    /**
     * Определение типа класса с учётом его аннотаций
     */
    fun determineClassType(
        className: String,
        packageName: String,
        classAnnotations: List<String> = emptyList()
    ): ClassType {
        val annotations = classAnnotations.map { it.removePrefix("@").lowercase() }

        if (annotations.any { it == "restcontroller" || it == "controller" }) {
            return ClassType.CONTROLLER
        }
        if (annotations.any { it == "service" }) {
            return ClassType.SERVICE
        }
        if (annotations.any { it == "repository" }) {
            return ClassType.REPOSITORY
        }
        if (annotations.any { it == "entity" || it == "table" }) {
            return ClassType.ENTITY
        }

        return determineClassType(className, packageName)
    }

    /**
     * Проверка, использует ли проект определённую аннотацию
     */
    fun usesAnnotation(annotation: String): Boolean {
        val normalized = annotation.removePrefix("@")
        return annotations.entries.any { (key, value) ->
            value > 0 && key.removePrefix("@").equals(normalized, ignoreCase = true)
        }
    }

    /**
     * Получение ожидаемого суффикса для типа класса
     */
    fun getExpectedSuffix(classType: ClassType): String {
        return when (classType) {
            ClassType.CONTROLLER -> "Controller"
            ClassType.SERVICE -> "Service"
            ClassType.REPOSITORY -> "Repository"
            ClassType.ENTITY -> ""
            ClassType.DTO -> "Dto"
            ClassType.OTHER -> ""
        }
    }
}

/**
 * Структура слоёв проекта
 */
data class LayerStructure(
    val controllers: List<ClassInfo> = emptyList(),
    val services: List<ClassInfo> = emptyList(),
    val repositories: List<ClassInfo> = emptyList(),
    val entities: List<ClassInfo> = emptyList(),
    val dtos: List<ClassInfo> = emptyList(),
    val other: List<ClassInfo> = emptyList()
)

/**
 * Информация о классе
 */
data class ClassInfo(
    val fullName: String,
    val simpleName: String,
    val packageName: String,
    val annotations: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val modifiers: List<String> = emptyList()
)

/**
 * Тип класса в архитектуре
 */
enum class ClassType {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    ENTITY,
    DTO,
    OTHER
}

/**
 * Тип слоя в архитектуре
 */
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

/**
 * Зависимость между классами
 */
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

/**
 * Соглашения по именованию в проекте
 */
data class NamingConventions(
    val serviceSuffix: String = "Service",
    val repositorySuffix: String = "Repository",
    val controllerSuffix: String = "Controller",
    val dtoSuffix: String = "Dto",
    val packagePrefix: String = "",
    val compliance: Map<String, Double> = emptyMap()
)