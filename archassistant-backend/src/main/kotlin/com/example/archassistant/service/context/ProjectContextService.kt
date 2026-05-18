package com.example.archassistant.service.context

import com.example.archassistant.model.context.ProjectContextSnapshot
import com.example.archassistant.service.context.cache.ProjectContextCacheService
import com.example.archassistant.service.context.classpath.ProjectPathResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProjectContextService(
    private val projectPathResolver: ProjectPathResolver,
    private val projectContextAssembler: ProjectContextAssembler,
    private val cacheService: ProjectContextCacheService
) {
    private val logger = LoggerFactory.getLogger(ProjectContextService::class.java)

    fun getProjectContext(
        projectId: String,
        refresh: Boolean = false,
        projectPathOverride: String? = null
    ): ProjectContextSnapshot? {
        val resolvedProjectPath = projectPathResolver.resolveProjectPath(projectId, projectPathOverride)

        if (refresh) {
            cacheService.invalidate(projectId)
        } else {
            cacheService.get(projectId)?.let { cached ->
                if (resolvedProjectPath.isBlank() || cached.projectPath == resolvedProjectPath) {
                    return cached
                }

                logger.debug(
                    "Cached project context is stale for projectId={}, invalidating cache (cachedPath={}, resolvedPath={})",
                    projectId,
                    cached.projectPath,
                    resolvedProjectPath
                )
                cacheService.invalidate(projectId)
            }
        }

        val built = projectContextAssembler.build(projectId, resolvedProjectPath) ?: return null
        cacheService.put(projectId, built)
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
        cacheService.invalidate(projectId)
    }
}