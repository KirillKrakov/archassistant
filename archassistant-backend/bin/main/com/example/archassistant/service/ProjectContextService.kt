package com.example.archassistant.service

import com.example.archassistant.model.ProjectContextSnapshot
import com.example.archassistant.util.ProjectClasspathResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ProjectContextService(
    private val projectStructureScanner: ProjectStructureScanner,
    private val ruleRepository: YamlRuleRepository
) {
    private val logger = LoggerFactory.getLogger(ProjectContextService::class.java)
    private val cache = ConcurrentHashMap<String, ProjectContextSnapshot>()

    fun getProjectContext(projectId: String, refresh: Boolean = false): ProjectContextSnapshot? {
        if (refresh) {
            cache.remove(projectId)
        }

        cache[projectId]?.let { return it }

        val built = buildProjectContext(projectId) ?: return null
        cache[projectId] = built
        return built
    }

    fun requireProjectContext(projectId: String, refresh: Boolean = false): ProjectContextSnapshot {
        return getProjectContext(projectId, refresh)
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

    private fun buildProjectContext(projectId: String): ProjectContextSnapshot? {
        val config = ruleRepository.load(projectId) ?: run {
            logger.warn("No configuration found for project {}", projectId)
            return null
        }

        val projectPath = resolveProjectPath(projectId, config)
        if (projectPath.isBlank()) {
            logger.warn("Project path is blank for project {}", projectId)
            return null
        }

        return try {
            val structure = projectStructureScanner.scanProject(projectPath, projectId)
            val outputDirs = ProjectClasspathResolver.resolveProjectOutputDirectories(projectPath)
            val compilationClasspath = ProjectClasspathResolver.buildCompilationClasspath(projectPath)

            ProjectContextSnapshot(
                projectId = projectId,
                projectPath = projectPath,
                structure = structure,
                classesDirPaths = outputDirs.map { it.toString() },
                compilationClasspath = compilationClasspath
            )
        } catch (e: Exception) {
            logger.warn("Failed to build project context for {}: {}", projectId, e.message)
            null
        }
    }

    private fun resolveProjectPath(projectId: String, config: Any): String {
        val fromConfig = runCatching {
            config::class.members.firstOrNull { it.name == "projectPath" }
                ?.call(config)
                ?.toString()
                ?.trim()
                .orEmpty()
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