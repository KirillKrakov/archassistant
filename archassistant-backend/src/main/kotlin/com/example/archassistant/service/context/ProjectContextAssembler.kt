package com.example.archassistant.service.context

import com.example.archassistant.model.*
import com.example.archassistant.service.context.detection.ArchitectureDetector
import com.example.archassistant.service.context.overlay.GeneratedSourceOverlayService
import com.example.archassistant.service.context.scanner.ProjectStructureScanner
import com.example.archassistant.util.ClasspathUtils
import com.example.archassistant.util.ProjectClasspathResolver
import com.example.archassistant.util.ProjectLayerClassifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

@Service
class ProjectContextAssembler(
    private val projectStructureScanner: ProjectStructureScanner,
    private val workspaceProjectScanner: WorkspaceProjectScanner,
    private val architectureDetector: ArchitectureDetector,
    private val generatedSourceOverlayService: GeneratedSourceOverlayService
) {

    private val logger = LoggerFactory.getLogger(ProjectContextAssembler::class.java)

    fun build(
        projectId: String,
        projectPath: String
    ): ProjectContextSnapshot? {
        if (projectPath.isBlank()) {
            logger.warn("Project path is blank for project {}", projectId)
            return null
        }

        return try {
            if (!Files.exists(Path.of(projectPath))) {
                logger.warn("Project path does not exist for project {}: {}", projectId, projectPath)
                return null
            }

            val overlayClassesDirs = generatedSourceOverlayService.compiledOverlayClassesDirs(projectId)

            val singleModuleStructure = runCatching {
                projectStructureScanner.scanProject(
                    projectPath = projectPath,
                    projectId = projectId,
                    additionalClassesDirs = overlayClassesDirs
                )
            }.getOrNull()

            if (singleModuleStructure != null) {
                return buildSingleModuleContext(
                    projectId = projectId,
                    projectPath = projectPath,
                    structure = singleModuleStructure,
                    overlayClassesDirs = overlayClassesDirs
                )
            }

            val moduleSuggestions = workspaceProjectScanner.scanWorkspace(projectPath, projectId)
            if (moduleSuggestions.isEmpty()) {
                logger.warn("Workspace scan returned no modules for project {}", projectId)
                return null
            }

            return buildWorkspaceContext(
                projectId = projectId,
                projectPath = projectPath,
                moduleSuggestions = moduleSuggestions,
                overlayClassesDirs = overlayClassesDirs
            )
        } catch (e: Exception) {
            logger.warn("Failed to build project context for {}: {}", projectId, e.message, e)
            null
        }
    }

    private fun buildSingleModuleContext(
        projectId: String,
        projectPath: String,
        structure: ProjectStructure,
        overlayClassesDirs: List<Path>
    ): ProjectContextSnapshot {
        val outputDirs = ProjectClasspathResolver.resolveProjectOutputDirectories(projectPath)
        val compilationClasspath = safeBuildClasspath(projectPath)

        val overlayClasspath = overlayClassesDirs.joinToString(File.pathSeparator) { it.toString() }
        val effectiveClasspath = mergeClasspathParts(
            listOf(
                overlayClasspath,
                compilationClasspath
            )
        )

        return ProjectContextSnapshot(
            projectId = projectId,
            projectPath = projectPath,
            structure = structure,
            classesDirPaths = (overlayClassesDirs + outputDirs)
                .map { it.toString() }
                .distinct(),
            compilationClasspath = effectiveClasspath,
            moduleRoots = emptyList()
        )
    }

    private fun buildWorkspaceContext(
        projectId: String,
        projectPath: String,
        moduleSuggestions: List<WorkspaceModuleSuggestions>,
        overlayClassesDirs: List<Path>
    ): ProjectContextSnapshot {
        val moduleRoots = moduleSuggestions
            .map { it.moduleRoot }
            .distinct()

        val moduleStructures = moduleSuggestions.map { suggestion ->
            projectStructureScanner.scanProject(
                projectPath = suggestion.moduleRoot,
                projectId = suggestion.moduleId,
                additionalClassesDirs = overlayClassesDirs
            )
        }

        val mergedStructure = mergeWorkspaceStructures(
            projectId = projectId,
            structures = moduleStructures
        )

        val outputDirs = moduleRoots
            .flatMap { ProjectClasspathResolver.resolveProjectOutputDirectories(it) }
            .distinctBy { it.toAbsolutePath().normalize().toString() }

        val rootClasspath = safeBuildClasspath(projectPath)
        val moduleClasspath = moduleRoots
            .map { safeBuildClasspath(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val overlayClasspath = overlayClassesDirs.joinToString(File.pathSeparator) { it.toString() }
        val effectiveClasspath = mergeClasspathParts(
            listOf(overlayClasspath, rootClasspath) + moduleClasspath
        )

        return ProjectContextSnapshot(
            projectId = projectId,
            projectPath = projectPath,
            structure = mergedStructure,
            classesDirPaths = (overlayClassesDirs + outputDirs)
                .map { it.toString() }
                .distinct(),
            compilationClasspath = effectiveClasspath,
            moduleRoots = moduleRoots
        )
    }

    private fun mergeWorkspaceStructures(
        projectId: String,
        structures: List<ProjectStructure>
    ): ProjectStructure {
        val mergedClassMap = linkedMapOf<String, ClassInfo>()

        structures
            .flatMap { it.classes }
            .forEach { classInfo ->
                val existing = mergedClassMap[classInfo.fullName]
                if (existing == null) {
                    mergedClassMap[classInfo.fullName] = classInfo
                } else if (existing.origin != ClassOrigin.OVERLAY && classInfo.origin == ClassOrigin.OVERLAY) {
                    mergedClassMap[classInfo.fullName] = classInfo
                }
            }

        val mergedClasses = mergedClassMap.values.toList()
        val mergedPackages = mergedClasses.map { it.packageName }.distinct()
        val mergedAnnotations = mergedClasses
            .flatMap { it.annotations }
            .groupingBy { it }
            .eachCount()

        val mergedDependencies = structures
            .flatMap { it.dependencies }
            .distinctBy { "${it.from}|${it.to}|${it.type}" }

        val mergedNamingConventions = extractNamingConventions(mergedClasses)
        val mergedLayers = extractLegacyLayerStructure(mergedClasses)
        val mergedLayerMap = extractLayerMap(mergedClasses)

        val provisional = ProjectStructure(
            projectId = projectId,
            packages = mergedPackages,
            classes = mergedClasses,
            layers = mergedLayers,
            layerMap = mergedLayerMap,
            annotations = mergedAnnotations,
            dependencies = mergedDependencies,
            namingConventions = mergedNamingConventions,
            scannedAt = LocalDateTime.now().toString()
        )

        val detection = architectureDetector.detect(provisional)
        val architecturePattern = detection.primaryProfile.toArchitecturePattern()

        return provisional.copy(
            architecturePattern = architecturePattern,
            detection = detection
        )
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

    private fun safeBuildClasspath(projectPath: String): String {
        return runCatching { ProjectClasspathResolver.buildCompilationClasspath(projectPath) }
            .getOrDefault("")
            .trim()
    }

    private fun mergeClasspathParts(parts: List<String>): String {
        return parts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .fold("") { acc, part ->
                if (acc.isBlank()) part else ClasspathUtils.mergeClasspathStrings(acc, part)
            }
    }
}