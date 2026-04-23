package com.example.archassistant.model

/**
 * Структура проекта, извлечённая при сканировании
 * Используется для предложения правил и анализа архитектуры
 */
data class ProjectStructure(
    val projectId: String,
    val architecturePattern: ArchitecturePattern? = null,
    val packages: List<String> = emptyList(),
    val layers: LayerStructure = LayerStructure(),
    val annotations: Map<String, Int> = emptyMap(),
    val dependencies: List<Dependency> = emptyList(),
    val namingConventions: NamingConventions = NamingConventions(),
    val violations: List<Violation> = emptyList(),
    val scannedAt: String = java.time.LocalDateTime.now().toString()
) {
    /**
     * Определение типа класса по аннотациям и расположению
     */
    fun determineClassType(className: String, packageName: String): ClassType {
        return when {
            // Сначала проверяем по пакету (более надёжный признак)
            packageName.contains("controller") -> ClassType.CONTROLLER
            packageName.contains("service") -> ClassType.SERVICE
            packageName.contains("repository") || packageName.contains("dao") -> ClassType.REPOSITORY
            packageName.contains("entity") || packageName.contains("model") -> ClassType.ENTITY
            packageName.contains("dto") || packageName.contains("vo") -> ClassType.DTO
            // Затем по имени класса (суффиксы)
            className.endsWith("Controller") -> ClassType.CONTROLLER
            className.endsWith("Service") -> ClassType.SERVICE
            className.endsWith("Repository") -> ClassType.REPOSITORY
            className.endsWith("Entity") -> ClassType.ENTITY
            className.endsWith("Dto") -> ClassType.DTO
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
        // Сначала проверяем аннотации конкретного класса
        if ("@RestController" in classAnnotations || "@Controller" in classAnnotations) {
            return ClassType.CONTROLLER
        }
        if ("@Service" in classAnnotations) {
            return ClassType.SERVICE
        }
        if ("@Repository" in classAnnotations) {
            return ClassType.REPOSITORY
        }
        if ("@Entity" in classAnnotations || "@Table" in classAnnotations) {
            return ClassType.ENTITY
        }

        // Fallback на имя пакета/класса
        return determineClassType(className, packageName)
    }

    /**
     * Проверка, использует ли проект определённую аннотацию
     */
    fun usesAnnotation(annotation: String): Boolean {
        return annotations.containsKey(annotation) && annotations[annotation]!! > 0
    }

    /**
     * Получение ожидаемого суффикса для типа класса
     */
    fun getExpectedSuffix(classType: ClassType): String {
        return when (classType) {
            ClassType.CONTROLLER -> "Controller"
            ClassType.SERVICE -> "Service"
            ClassType.REPOSITORY -> "Repository"
            ClassType.ENTITY -> ""  // Entity могут иметь любые имена
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
    val modifiers: List<String> = emptyList()  // public, abstract, final, etc.
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
 * Зависимость между классами
 */
data class Dependency(
    val from: String,      // FullName класса-источника
    val to: String,        // FullName класса-цели
    val type: DependencyType = DependencyType.IMPORT
)

enum class DependencyType {
    IMPORT,        // Прямой импорт
    FIELD,         // Поле типа
    METHOD_PARAM,  // Параметр метода
    RETURN_TYPE,   // Тип возвращаемого значения
    INHERITANCE    // Наследование/имплементация
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
    val compliance: Map<String, Double> = emptyMap()  // % соответствия по типу
)