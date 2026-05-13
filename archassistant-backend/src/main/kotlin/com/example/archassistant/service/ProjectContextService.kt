package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.util.ClasspathUtils
import com.example.archassistant.util.ProjectClasspathResolver
import com.example.archassistant.util.ProjectLayerClassifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class ProjectContextService(
    private val projectStructureScanner: ProjectStructureScanner,
    private val workspaceProjectScanner: WorkspaceProjectScanner,
    private val architectureDetector: ArchitectureDetector,
    private val ruleRepository: YamlRuleRepository,
    private val generatedSourceOverlayService: GeneratedSourceOverlayService
) {
    private val logger = LoggerFactory.getLogger(ProjectContextService::class.java)
    private val cache = ConcurrentHashMap<String, ProjectContextSnapshot>()

    fun getProjectContext(
        projectId: String,
        refresh: Boolean = false,
        projectPathOverride: String? = null
    ): ProjectContextSnapshot? {
        if (refresh) {
            cache.remove(projectId)
        }

        cache[projectId]?.let { return it }

        val built = buildProjectContext(projectId, projectPathOverride) ?: return null
        cache[projectId] = built
        return built
    }

    fun requireProjectContext(
        projectId: String,
        refresh: Boolean = false,
        projectPathOverride: String? = null
    ): ProjectContextSnapshot {
        return getProjectContext(projectId, refresh, projectPathOverride)
            ?: throw IllegalStateException(
                "Project context is unavailable for projectId='$projectId'. " +
                        "Set projectPath in configuration before code generation."
            )
    }

    fun invalidate(projectId: String) {
        cache.remove(projectId)
    }

    private fun buildProjectContext(
        projectId: String,
        projectPathOverride: String? = null
    ): ProjectContextSnapshot? {
        val config = ruleRepository.load(projectId)
        val projectPath = resolveProjectPath(projectId, config, projectPathOverride)

        if (projectPath.isBlank()) {
            logger.warn("Project path is blank for project {}", projectId)
            return null
        }

        return try {
            val overlayClassesDirs = generatedSourceOverlayService.compiledOverlayClassesDirs(projectId)

            // 1) Сначала пробуем старую ветку для обычных single-module проектов
            val singleModuleStructure = runCatching {
                projectStructureScanner.scanProject(
                    projectPath = projectPath,
                    projectId = projectId,
                    additionalClassesDirs = overlayClassesDirs
                )
            }.getOrNull()

            if (singleModuleStructure != null) {
                val outputDirs = ProjectClasspathResolver.resolveProjectOutputDirectories(projectPath)
                val compilationClasspath = safeBuildClasspath(projectPath)

                val overlayClasspath = overlayClassesDirs.joinToString(File.pathSeparator) { it.toString() }
                val effectiveClasspath = mergeClasspathParts(listOf(
                    overlayClasspath,
                    compilationClasspath
                ))

                return ProjectContextSnapshot(
                    projectId = projectId,
                    projectPath = projectPath,
                    structure = singleModuleStructure,
                    classesDirPaths = (overlayClassesDirs + outputDirs)
                        .map { it.toString() }
                        .distinct(),
                    compilationClasspath = effectiveClasspath,
                    moduleRoots = emptyList()
                )
            }

            // 2) Если корневой проект без compiled classes — это workspace/microservices case
            val moduleSuggestions = workspaceProjectScanner.scanWorkspace(projectPath, projectId)
            if (moduleSuggestions.isEmpty()) {
                logger.warn("Workspace scan returned no modules for project {}", projectId)
                return null
            }

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
                projectPath = projectPath,
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

            ProjectContextSnapshot(
                projectId = projectId,
                projectPath = projectPath,
                structure = mergedStructure,
                classesDirPaths = (overlayClassesDirs + outputDirs)
                    .map { it.toString() }
                    .distinct(),
                compilationClasspath = effectiveClasspath,
                moduleRoots = moduleRoots
            )
        } catch (e: Exception) {
            logger.warn("Failed to build project context for {}: {}", projectId, e.message, e)
            null
        }
    }

    private fun mergeWorkspaceStructures(
        projectId: String,
        projectPath: String,
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

    private fun resolveProjectPath(
        projectId: String,
        config: Any?,
        projectPathOverride: String? = null
    ): String {
        projectPathOverride?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        val fromConfig = runCatching {
            config?.let { cfg ->
                cfg::class.members.firstOrNull { it.name == "projectPath" }
                    ?.call(cfg)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            }.orEmpty()
        }.getOrDefault("")

        if (fromConfig.isNotBlank()) {
            return fromConfig
        }

        val envCandidates = listOf(
            "ARCHASSISTANT_PROJECT_PATH_${projectId.uppercase()}",
            "ARCHASSISTANT_PROJECT_PATH",
            "PROJECT_PATH"
        )

        return envCandidates
            .asSequence()
            .mapNotNull { key -> System.getenv(key)?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
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