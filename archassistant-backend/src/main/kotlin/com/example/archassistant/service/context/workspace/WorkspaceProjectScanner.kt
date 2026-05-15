package com.example.archassistant.service.context.workspace

import com.example.archassistant.service.context.classpath.ProjectPathResolver
import com.example.archassistant.service.context.detection.ArchitectureDetector
import com.example.archassistant.service.context.scanner.ProjectStructureScanner
import com.example.archassistant.service.rules.template.RuleTemplateEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

@Service
class WorkspaceProjectScanner(
    private val projectStructureScanner: ProjectStructureScanner,
    private val architectureDetector: ArchitectureDetector,
    private val ruleTemplateEngine: RuleTemplateEngine,
    private val projectPathResolver: ProjectPathResolver
) {

    private val logger = LoggerFactory.getLogger(WorkspaceProjectScanner::class.java)

    fun scanWorkspace(workspacePath: String, workspaceId: String): List<WorkspaceModuleSuggestions> {
        val workspaceRoot = Paths.get(workspacePath)
        if (!Files.exists(workspaceRoot)) {
            throw IllegalArgumentException("Workspace path does not exist: $workspacePath")
        }

        val moduleRoots = discoverModuleRoots(workspaceRoot)

        if (moduleRoots.isEmpty()) {
            throw IllegalArgumentException("No compiled module roots found under $workspacePath")
        }

        return moduleRoots.map { moduleRoot ->
            val moduleId = moduleId(workspaceRoot, moduleRoot, workspaceId)

            logger.info("Scanning workspace module: {} at {}", moduleId, moduleRoot)

            val structure = projectStructureScanner.scanProject(moduleRoot.toString(), moduleId)
            val profile = architectureDetector.detect(structure)
            val rules = ruleTemplateEngine.suggestRules(structure)

            WorkspaceModuleSuggestions(
                moduleId = moduleId,
                moduleRoot = moduleRoot.toString(),
                profile = profile,
                rules = rules
            )
        }
    }

    fun scanProjectFromConfig(projectId: String): List<WorkspaceModuleSuggestions>? {
        val projectPath = projectPathResolver.resolveProjectPath(projectId)
        if (projectPath.isBlank()) {
            return null
        }

        return if (Files.exists(Paths.get(projectPath))) {
            scanWorkspace(projectPath, projectId)
        } else {
            logger.warn("Project path not found: {}", projectPath)
            null
        }
    }

    private fun discoverModuleRoots(workspaceRoot: Path): List<Path> {
        val roots = mutableSetOf<Path>()

        Files.walk(workspaceRoot).use { stream ->
            stream
                .filter { Files.isDirectory(it) }
                .forEach { dir ->
                    if (isCompiledClassesDir(dir)) {
                        roots.add(moduleRootFromClassesDir(dir))
                    }
                }
        }

        return roots.toList().distinct()
    }

    private fun isCompiledClassesDir(dir: Path): Boolean {
        val normalized = dir.toString().replace('\\', '/')
        return normalized.endsWith("/target/classes") ||
                normalized.endsWith("/build/classes/java/main") ||
                normalized.endsWith("/build/classes/kotlin/main") ||
                normalized.contains("/out/production/")
    }

    private fun moduleRootFromClassesDir(classesDir: Path): Path {
        val normalized = classesDir.toString().replace('\\', '/')

        return when {
            normalized.endsWith("/target/classes") ->
                classesDir.parent?.parent ?: classesDir

            normalized.endsWith("/build/classes/java/main") ||
                    normalized.endsWith("/build/classes/kotlin/main") ->
                classesDir.parent?.parent?.parent?.parent ?: classesDir

            normalized.contains("/out/production/") ->
                classesDir.parent?.parent?.parent ?: classesDir

            else ->
                classesDir.parent?.parent ?: classesDir
        }
    }

    private fun moduleId(workspaceRoot: Path, moduleRoot: Path, workspaceId: String): String {
        val relative = runCatching { workspaceRoot.relativize(moduleRoot).toString() }
            .getOrDefault(moduleRoot.fileName?.name ?: workspaceId)

        val safe = relative
            .replace('\\', '/')
            .replace('/', '-')
            .replace("[^A-Za-z0-9._-]".toRegex(), "_")
            .trim('-')

        return if (safe.isBlank()) workspaceId else "$workspaceId-$safe"
    }
}