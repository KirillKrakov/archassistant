package com.example.archassistant.service.context.scanner

import com.example.archassistant.model.*
import com.example.archassistant.model.context.*
import com.example.archassistant.service.context.classpath.ProjectPathResolver
import com.example.archassistant.service.context.detection.ArchitectureDetector
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class ProjectStructureScanner(
    private val architectureDetector: ArchitectureDetector,
    private val projectPathResolver: ProjectPathResolver
) {

    private data class ImportedClassEntry(
        val javaClass: JavaClass,
        val origin: ClassOrigin
    )

    private val logger = LoggerFactory.getLogger(ProjectStructureScanner::class.java)

    fun scanProject(
        projectPath: String,
        projectId: String,
        additionalClassesDirs: List<Path> = emptyList()
    ): ProjectStructure {
        logger.info("Scanning project: {} at path: {}", projectId, projectPath)

        val classesDir = findClassesDirectory(projectPath)
            ?: throw IllegalArgumentException("Cannot find compiled classes in $projectPath")

        val mergedClasses = linkedMapOf<String, ImportedClassEntry>()

        fun importFrom(dir: Path, origin: ClassOrigin) {
            if (!Files.exists(dir) || !Files.isDirectory(dir)) return
            val imported = ClassFileImporter().importPath(dir)
            imported.forEach { javaClass ->
                mergedClasses[javaClass.name] = ImportedClassEntry(javaClass, origin)
            }
        }

        importFrom(classesDir, ClassOrigin.BASE)
        additionalClassesDirs
            .distinctBy { it.toAbsolutePath().normalize().toString() }
            .forEach { importFrom(it, ClassOrigin.OVERLAY) }

        val importedClasses = mergedClasses.values.map { it.javaClass }

        val classInfos = extractClassInfos(mergedClasses.values)
        val packages = classInfos.map { it.packageName }.distinct()
        val annotations = extractAnnotations(importedClasses)
        val dependencies = extractDependencies(importedClasses)
        val namingConventions = extractNamingConventions(classInfos)
        val layers = extractLegacyLayerStructure(classInfos)
        val layerMap = extractLayerMap(classInfos)

        val provisional = ProjectStructure(
            projectId = projectId,
            packages = packages,
            classes = classInfos,
            layers = layers,
            layerMap = layerMap,
            annotations = annotations,
            dependencies = dependencies,
            namingConventions = namingConventions
        )

        val detection = architectureDetector.detect(provisional)
        val architecturePattern = detection.primaryProfile.toArchitecturePattern()

        return provisional.copy(
            architecturePattern = architecturePattern,
            detection = detection
        )
    }

    fun scanProjectFromConfig(projectId: String): ProjectStructure? {
        val projectPath = projectPathResolver.resolveProjectPath(projectId)
        if (projectPath.isBlank()) {
            return null
        }

        return if (Files.exists(Paths.get(projectPath))) {
            scanProject(projectPath, projectId)
        } else {
            logger.warn("Project path not found: {}", projectPath)
            null
        }
    }

    @Deprecated("Use scanProjectFromConfig(projectId) and centralized path resolution.")
    fun scanProjectFromConfig(
        projectId: String,
        ruleRepository: YamlRuleRepository
    ): ProjectStructure? {
        return scanProjectFromConfig(projectId)
    }

    private fun findClassesDirectory(projectPath: String): Path? {
        val projectName = Paths.get(projectPath).fileName?.toString()
            ?: projectPath.substringAfterLast('/')

        val possiblePaths = listOf(
            Paths.get(projectPath, "build", "classes", "kotlin", "main"),
            Paths.get(projectPath, "build", "classes", "java", "main"),
            Paths.get(projectPath, "target", "classes"),
            Paths.get(projectPath, "out", "production", projectName)
        )

        return possiblePaths.firstOrNull { Files.exists(it) && Files.isDirectory(it) }
    }

    private fun extractClassInfos(entries: Iterable<ImportedClassEntry>): List<ClassInfo> {
        return entries.map { entry ->
            val javaClass = entry.javaClass
            val simpleName = javaClass.name.substringAfterLast('.')

            val annotations = javaClass.annotations
                .map { annotation -> annotation.type.name.substringAfterLast('.') }
                .distinct()

            val dependencies = javaClass.directDependenciesFromSelf
                .map { dep -> dep.targetClass.name }
                .filterNot { target ->
                    target.startsWith("java.") ||
                            target.startsWith("javax.") ||
                            target.startsWith("kotlin.") ||
                            target.startsWith("org.springframework.")
                }
                .distinct()

            val publicMethods = javaClass.methods
                .filter { it.modifiers.contains(JavaModifier.PUBLIC) }
                .map { method ->
                    val params = method.rawParameterTypes.joinToString(", ") { it.name.substringAfterLast('.') }
                    val returnType = method.rawReturnType.name.substringAfterLast('.')
                    "${method.name}($params): $returnType"
                }
                .distinct()

            val fields = javaClass.fields
                .map { field ->
                    FieldInfo(
                        name = field.name,
                        type = field.rawType.name,
                        modifiers = field.modifiers.map { it.name }
                    )
                }
                .sortedBy { it.name }

            val constructors = javaClass.constructors
                .map { constructor ->
                    ConstructorInfo(
                        parameters = constructor.rawParameterTypes.map { it.name },
                        modifiers = constructor.modifiers.map { it.name }
                    )
                }
                .sortedBy { it.parameters.size }

            val kind = when {
                javaClass.isInterface -> ClassKind.INTERFACE
                javaClass.isEnum -> ClassKind.ENUM
                javaClass.isAnnotation -> ClassKind.ANNOTATION
                else -> ClassKind.CLASS
            }

            ClassInfo(
                fullName = javaClass.name,
                simpleName = simpleName,
                packageName = javaClass.packageName,
                kind = kind,
                superClass = javaClass.rawSuperclass.orElse(null)?.name,
                interfaces = javaClass.rawInterfaces.map { it.name }.distinct(),
                annotations = annotations,
                dependencies = dependencies,
                modifiers = javaClass.modifiers.map { it.name },
                fields = fields,
                constructors = constructors,
                publicMethods = publicMethods,
                origin = entry.origin
            )
        }
    }

    private fun extractAnnotations(classes: Iterable<JavaClass>): Map<String, Int> {
        return classes
            .flatMap { javaClass ->
                javaClass.annotations.map { annotation ->
                    annotation.type.name.substringAfterLast('.')
                }
            }
            .groupingBy { it }
            .eachCount()
    }

    private fun extractDependencies(classes: Iterable<JavaClass>): List<Dependency> {
        return classes
            .flatMap { sourceClass ->
                sourceClass.directDependenciesFromSelf.mapNotNull { dep ->
                    val targetName = dep.targetClass.name
                    if (
                        targetName.startsWith("java.") ||
                        targetName.startsWith("javax.") ||
                        targetName.startsWith("kotlin.") ||
                        targetName.startsWith("org.springframework.")
                    ) {
                        null
                    } else {
                        Dependency(
                            from = sourceClass.name,
                            to = targetName,
                            type = when {
                                dep.targetClass.isInterface -> DependencyType.INHERITANCE
                                else -> DependencyType.IMPORT
                            }
                        )
                    }
                }
            }
            .distinctBy { "${it.from}|${it.to}|${it.type}" }
    }

    private fun extractLegacyLayerStructure(classInfos: List<ClassInfo>): LayerStructure {
        fun byType(type: ClassType): List<ClassInfo> {
            return classInfos.filter { info -> ProjectLayerClassifier.matchesClassType(info, type) }
        }

        return LayerStructure(
            controllers = byType(ClassType.CONTROLLER),
            services = byType(ClassType.SERVICE),
            repositories = byType(ClassType.REPOSITORY),
            entities = byType(ClassType.ENTITY),
            dtos = byType(ClassType.DTO),
            other = classInfos.filter { info -> ProjectLayerClassifier.classify(info) == LayerType.OTHER }
        )
    }

    private fun extractLayerMap(classInfos: List<ClassInfo>): Map<LayerType, List<ClassInfo>> {
        val buckets = LayerType.entries.associateWith { mutableListOf<ClassInfo>() }.toMutableMap()
        classInfos.forEach { info ->
            val layer = ProjectLayerClassifier.classify(info)
            buckets.getValue(layer).add(info)
        }
        return buckets.mapValues { it.value.toList() }
    }

    private fun extractNamingConventions(classInfos: List<ClassInfo>): NamingConventions {
        val serviceClasses = classInfos.filter { info ->
            info.packageName.contains("service", ignoreCase = true) ||
                    info.annotations.any { it.equals("Service", ignoreCase = true) }
        }

        val repositoryClasses = classInfos.filter { info ->
            info.packageName.contains("repository", ignoreCase = true) ||
                    info.annotations.any { it.equals("Repository", ignoreCase = true) }
        }

        val controllerClasses = classInfos.filter { info ->
            info.packageName.contains("controller", ignoreCase = true) ||
                    info.annotations.any { it.equals("Controller", ignoreCase = true) || it.equals("RestController", ignoreCase = true) }
        }

        val serviceSuffix = findMostCommonSuffix(serviceClasses, "Service")
        val repositorySuffix = findMostCommonSuffix(repositoryClasses, "Repository")
        val controllerSuffix = findMostCommonSuffix(controllerClasses, "Controller")

        return NamingConventions(
            serviceSuffix = serviceSuffix,
            repositorySuffix = repositorySuffix,
            controllerSuffix = controllerSuffix
        )
    }

    private fun findMostCommonSuffix(classes: List<ClassInfo>, default: String): String {
        if (classes.isEmpty()) return default

        val suffixes = classes
            .map { it.simpleName }
            .mapNotNull { name ->
                when {
                    name.endsWith("Service") -> "Service"
                    name.endsWith("ServiceImpl") -> "ServiceImpl"
                    name.endsWith("Repository") -> "Repository"
                    name.endsWith("Controller") -> "Controller"
                    name.endsWith("ControllerImpl") -> "ControllerImpl"
                    name.endsWith("Dto") -> "Dto"
                    name.endsWith("ViewModel") -> "ViewModel"
                    else -> null
                }
            }
            .groupingBy { it }
            .eachCount()

        return suffixes.maxByOrNull { it.value }?.key ?: default
    }
}