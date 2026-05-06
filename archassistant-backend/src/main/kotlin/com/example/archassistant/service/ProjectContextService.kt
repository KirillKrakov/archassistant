package com.example.archassistant.service

import com.example.archassistant.model.ProjectContextSnapshot
import com.example.archassistant.util.ClasspathUtils
import com.example.archassistant.util.ProjectClasspathResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service
class ProjectContextService(
    private val projectStructureScanner: ProjectStructureScanner,
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

    fun clear() {
        cache.clear()
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
            val structure = projectStructureScanner.scanProject(
                projectPath = projectPath,
                projectId = projectId,
                additionalClassesDirs = overlayClassesDirs
            )
            val outputDirs = ProjectClasspathResolver.resolveProjectOutputDirectories(projectPath)
            val compilationClasspath = ProjectClasspathResolver.buildCompilationClasspath(projectPath)

            val overlayClasspath = overlayClassesDirs.joinToString(File.pathSeparator) { it.toString() }
            val effectiveClasspath = ClasspathUtils.mergeClasspathStrings(
                overlayClasspath,
                compilationClasspath
            )

            ProjectContextSnapshot(
                projectId = projectId,
                projectPath = projectPath,
                structure = structure,
                classesDirPaths = (overlayClassesDirs + outputDirs)
                    .map { it.toString() }
                    .distinct(),
                compilationClasspath = effectiveClasspath
            )
        } catch (e: Exception) {
            logger.warn("Failed to build project context for {}: {}", projectId, e.message)
            null
        }
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
}