package com.example.archassistant.service.context.overlay

import com.example.archassistant.dto.GeneratedFilePayload
import com.example.archassistant.dto.GeneratedFileSyncResponse
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import com.example.archassistant.util.ClasspathUtils
import com.example.archassistant.util.CompilationException
import com.example.archassistant.util.ProjectClasspathResolver
import com.example.archassistant.util.RuntimeClasspathResolver
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.io.path.isDirectory

@Service
class GeneratedSourceOverlayService(
    private val ruleRepository: YamlRuleRepository,
    @Value("\${archassistant.config-root:.archassistant}")
    private val configRootPath: String
) {

    private val logger = LoggerFactory.getLogger(GeneratedSourceOverlayService::class.java)
    private val javaCompiler = ToolProvider.getSystemJavaCompiler()
        ?: throw IllegalStateException("Java compiler not available. Use JDK, not JRE.")

    fun overlaySourceDir(projectId: String): Path = overlayRoot(projectId).resolve("sources")
    fun overlayClassesDir(projectId: String): Path = overlayRoot(projectId).resolve("classes")

    fun compiledOverlayClassesDirs(projectId: String): List<Path> {
        val dir = overlayClassesDir(projectId)
        return if (Files.exists(dir) && dir.isDirectory()) listOf(dir) else emptyList()
    }

    fun syncGeneratedFiles(
        projectId: String,
        projectPathOverride: String? = null,
        files: List<GeneratedFilePayload> = emptyList()
    ): GeneratedFileSyncResponse {
        val resolvedProjectPath = resolveProjectPath(projectId, projectPathOverride)

        if (resolvedProjectPath.isBlank()) {
            return GeneratedFileSyncResponse(
                success = false,
                projectId = projectId,
                error = "Project path is unavailable for projectId='$projectId'. Set projectPath first or pass it in sync request."
            )
        }

        if (!Files.exists(Path.of(resolvedProjectPath))) {
            return GeneratedFileSyncResponse(
                success = false,
                projectId = projectId,
                projectPath = resolvedProjectPath,
                error = "Project path does not exist: $resolvedProjectPath"
            )
        }

        if (files.isEmpty()) {
            return GeneratedFileSyncResponse(
                success = true,
                projectId = projectId,
                projectPath = resolvedProjectPath,
                syncedFiles = 0,
                compiledSources = 0,
                overlaySourceDir = overlaySourceDir(projectId).toString(),
                overlayClassesDir = overlayClassesDir(projectId).toString(),
                warnings = listOf("No files provided; overlay left unchanged.")
            )
        }

        val sourceRoot = overlaySourceDir(projectId)
        val classesRoot = overlayClassesDir(projectId)

        Files.createDirectories(sourceRoot)

        val writtenFiles = files.map { payload ->
            writeOverlayFile(sourceRoot, payload)
        }.distinctBy { it.toAbsolutePath().normalize().toString() }

        val compilation = compileOverlaySources(
            projectId = projectId,
            projectPath = resolvedProjectPath,
            classesRoot = classesRoot
        )

        return if (!compilation.success) {
            GeneratedFileSyncResponse(
                success = false,
                projectId = projectId,
                projectPath = resolvedProjectPath,
                syncedFiles = files.size,
                compiledSources = 0,
                overlaySourceDir = sourceRoot.toString(),
                overlayClassesDir = classesRoot.toString(),
                error = compilation.error,
                warnings = compilation.warnings
            )
        } else {
            GeneratedFileSyncResponse(
                success = true,
                projectId = projectId,
                projectPath = resolvedProjectPath,
                syncedFiles = files.size,
                compiledSources = writtenFiles.size,
                overlaySourceDir = sourceRoot.toString(),
                overlayClassesDir = classesRoot.toString(),
                warnings = compilation.warnings
            )
        }
    }

    fun clearOverlay(projectId: String): Boolean {
        val root = overlayRoot(projectId)
        return try {
            if (Files.exists(root)) {
                deleteRecursively(root)
            }
            true
        } catch (e: Exception) {
            logger.warn("Failed to clear overlay for {}: {}", projectId, e.message)
            false
        }
    }

    private fun compileOverlaySources(
        projectId: String,
        projectPath: String,
        classesRoot: Path
    ): OverlayCompilationResult {
        val allSourceFiles = collectOverlaySources(overlaySourceDir(projectId))

        if (allSourceFiles.isEmpty()) {
            return OverlayCompilationResult(
                success = true,
                warnings = listOf("Overlay sources are empty; nothing to compile.")
            )
        }

        val hasJava = allSourceFiles.any { it.toString().endsWith(".java") }
        val hasKotlin = allSourceFiles.any { it.toString().endsWith(".kt") }

        if (hasJava && hasKotlin) {
            return OverlayCompilationResult(
                success = false,
                error = "Mixed Java/Kotlin overlay output is not supported yet",
                warnings = listOf("Compile the overlay as either pure Java or pure Kotlin for now.")
            )
        }

        val stagingRoot = overlayRoot(projectId).resolve(".staging").resolve(UUID.randomUUID().toString())
        val stagingClassesDir = stagingRoot.resolve("classes")

        try {
            Files.createDirectories(stagingClassesDir)

            if (hasKotlin) {
                compileKotlin(allSourceFiles, stagingClassesDir, projectPath)
            } else {
                compileJava(allSourceFiles, stagingClassesDir, projectPath)
            }

            replaceDirectory(stagingClassesDir, classesRoot)
            deleteRecursively(stagingRoot)

            return OverlayCompilationResult(
                success = true,
                compiledFiles = allSourceFiles.size
            )
        } catch (e: Exception) {
            deleteRecursively(stagingRoot)
            logger.warn("Overlay compilation failed for {}: {}", projectId, e.message)
            return OverlayCompilationResult(
                success = false,
                error = e.message ?: "Overlay compilation failed"
            )
        }
    }

    private fun compileKotlin(
        sourceFiles: List<Path>,
        outputDir: Path,
        projectPath: String
    ) {
        val compiler = K2JVMCompiler()
        val effectiveClasspath = effectiveClasspath(projectPath)

        val args = mutableListOf(
            "-d", outputDir.toString()
        )

        if (effectiveClasspath.isNotBlank()) {
            args += listOf("-classpath", effectiveClasspath)
        }

        args += sourceFiles.map { it.toString() }

        val errStream = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errStream))

        val exitCode = try {
            compiler.exec(System.err, *args.toTypedArray())
        } finally {
            System.setErr(originalErr)
        }

        if (exitCode != ExitCode.OK) {
            val errorMsg = errStream.toString("UTF-8")
            throw CompilationException("Kotlin overlay compilation failed:\n$errorMsg")
        }
    }

    private fun compileJava(
        sourceFiles: List<Path>,
        outputDir: Path,
        projectPath: String
    ) {
        val fileManager = javaCompiler.getStandardFileManager(null, null, null)
        try {
            val compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                sourceFiles.map { it.toFile() }
            )

            val effectiveClasspath = effectiveClasspath(projectPath)

            val options = mutableListOf("-d", outputDir.toString())
            if (effectiveClasspath.isNotBlank()) {
                options.addAll(listOf("-classpath", effectiveClasspath))
            }

            val diagnostics = DiagnosticCollector<JavaFileObject>()
            val outWriter = OutputStreamWriter(System.out)

            val task = javaCompiler.getTask(
                outWriter,
                fileManager,
                diagnostics,
                options,
                null,
                compilationUnits
            )

            val success = task.call()
            if (!success) {
                val details = diagnostics.diagnostics.joinToString(separator = "\n") { diagnostic ->
                    val line = diagnostic.lineNumber.takeIf { it >= 0 } ?: -1
                    "${diagnostic.kind}: ${diagnostic.getMessage(null)} at line $line"
                }
                throw CompilationException(
                    if (details.isBlank()) "Java overlay compilation failed" else "Java overlay compilation failed:\n$details"
                )
            }
        } finally {
            fileManager.close()
        }
    }

    private fun effectiveClasspath(projectPath: String): String {
        val runtimeEntries = RuntimeClasspathResolver.resolveRuntimeClasspathEntries().map { it.toString() }
        val projectClasspath = ProjectClasspathResolver.buildCompilationClasspath(projectPath)

        return ClasspathUtils.mergeClasspathStrings(
            runtimeEntries.joinToString(File.pathSeparator),
            projectClasspath
        )
    }

    private fun writeOverlayFile(sourceRoot: Path, payload: GeneratedFilePayload): Path {
        val language = payload.language?.trim()?.lowercase()
        val kotlinByHint = language == "kotlin"
        val kotlinByCode = detectKotlin(payload.code)
        val isKotlin = kotlinByHint || (!kotlinByHint && kotlinByCode)

        val sourceDir = if (isKotlin) "kotlin" else "java"
        val extension = if (isKotlin) "kt" else "java"

        val packageName = effectivePackageName(payload)
        val simpleClassName = payload.className.substringAfterLast('.')
        val packagePath = packageName?.replace('.', File.separatorChar)?.trim(File.separatorChar) ?: ""

        val target = if (packagePath.isBlank()) {
            sourceRoot.resolve(sourceDir).resolve("$simpleClassName.$extension")
        } else {
            sourceRoot.resolve(sourceDir).resolve(packagePath).resolve("$simpleClassName.$extension")
        }

        Files.createDirectories(target.parent)
        Files.writeString(target, payload.code)

        val siblingExtension = if (isKotlin) "java" else "kt"
        val siblingTarget = target.resolveSibling("$simpleClassName.$siblingExtension")
        if (Files.exists(siblingTarget)) {
            Files.deleteIfExists(siblingTarget)
        }

        return target
    }

    private fun collectOverlaySources(sourceRoot: Path): List<Path> {
        if (!Files.exists(sourceRoot)) return emptyList()

        Files.walk(sourceRoot).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) && (it.toString().endsWith(".java") || it.toString().endsWith(".kt")) }
                .sorted()
                .toList()
        }
    }

    private fun effectivePackageName(payload: GeneratedFilePayload): String? {
        val fromPayload = payload.packageName?.trim()?.takeIf { it.isNotBlank() }
        if (fromPayload != null) return fromPayload

        return Regex("""^\s*package\s+([A-Za-z0-9_.]+)""", RegexOption.MULTILINE)
            .find(payload.code)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun detectKotlin(code: String): Boolean {
        val kotlinKeywords = listOf("fun ", "val ", "var ", "?:", "!!", "data class", "sealed class", "object ")
        val isKotlin = kotlinKeywords.any { code.contains(it) }
        if (!isKotlin && code.contains("class ") && !code.contains(";")) {
            return true
        }
        return isKotlin
    }

    private fun resolveProjectPath(projectId: String, explicitProjectPath: String?): String {
        explicitProjectPath?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        val config = ruleRepository.load(projectId) ?: return resolveFromEnv(projectId)

        val fromConfig = config.projectPath?.trim().orEmpty()

        if (fromConfig.isNotBlank()) {
            return fromConfig
        }

        return resolveFromEnv(projectId)
    }

    private fun resolveFromEnv(projectId: String): String {
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

    private fun overlayRoot(projectId: String): Path {
        val safeProjectId = projectId.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return Path.of(configRootPath, "generated-overlays", safeProjectId)
    }

    private fun replaceDirectory(source: Path, target: Path) {
        if (Files.exists(target)) {
            deleteRecursively(target)
        }

        Files.createDirectories(target.parent)
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return

        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    private data class OverlayCompilationResult(
        val success: Boolean,
        val compiledFiles: Int = 0,
        val error: String? = null,
        val warnings: List<String> = emptyList()
    )
}