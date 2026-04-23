package com.example.archassistant.model

/**
 * Контекст для применения шаблона правил
 * Содержит конкретные данные проекта для параметризации шаблона
 */
data class TemplateContext(
    val projectId: String,
    val basePackage: String,
    val architecturePattern: ArchitecturePattern,
    val layers: Map<LayerType, List<PackageInfo>>,
    val annotations: Map<String, Int>,
    val namingConventions: NamingConventions,
    val dependencies: List<DependencyInfo> = emptyList()
) {
    /**
     * Получение пакетов для определённого типа слоя
     */
    fun getPackagesForLayer(layerType: LayerType): List<String> {
        return layers[layerType]?.map { it.packageName } ?: emptyList()
    }

    /**
     * Проверка, используется ли определённая аннотация в проекте
     */
    fun usesAnnotation(annotation: String): Boolean {
        return annotations.containsKey(annotation) && annotations[annotation]!! > 0
    }

    /**
     * Получение ожидаемого суффикса для типа слоя
     */
    fun getExpectedSuffix(layerType: LayerType): String {
        return when (layerType) {
            LayerType.CONTROLLER -> namingConventions.controllerSuffix
            LayerType.SERVICE -> namingConventions.serviceSuffix
            LayerType.REPOSITORY -> namingConventions.repositorySuffix
            LayerType.ENTITY -> ""
            LayerType.DTO -> namingConventions.dtoSuffix
            LayerType.OTHER -> ""
            else -> ""
        }
    }
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
    OTHER
}

/**
 * Информация о пакете
 */
data class PackageInfo(
    val packageName: String,
    val classCount: Int,
    val annotations: List<String> = emptyList(),
    val dependencies: List<String> = emptyList()
)

/**
 * Информация о зависимости
 */
data class DependencyInfo(
    val fromPackage: String,
    val toPackage: String,
    val dependencyType: DependencyType,
    val classCount: Int = 1
)