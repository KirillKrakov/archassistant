package com.example.archassistant.service.context.overlay

import com.example.archassistant.dto.generatedfiles.request.GeneratedFilePayload
import com.example.archassistant.dto.generatedfiles.response.GeneratedFileSyncResponse
import com.example.archassistant.exception.CompilationException
import com.example.archassistant.service.context.classpath.ProjectClasspathResolver
import com.example.archassistant.service.context.classpath.ProjectPathResolver
import com.example.archassistant.service.generation.validation.classpath.RuntimeClasspathResolver
import com.example.archassistant.util.classpath.ClasspathUtils
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
    private val projectPathResolver: ProjectPathResolver,
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
        val resolvedProjectPath = projectPathResolver.resolveProjectPath(projectId, projectPathOverride)

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
        val stagingRoot = overlayRoot(projectId).resolve(".staging").resolve(UUID.randomUUID().toString())
        val stagingSourceRoot = stagingRoot.resolve("sources")
        val stagingClassesRoot = stagingRoot.resolve("classes")

        return try {
            Files.createDirectories(stagingSourceRoot)
            Files.createDirectories(stagingClassesRoot)

            val writtenFiles = files.map { payload ->
                writeOverlayFile(stagingSourceRoot, payload)
            }.distinctBy { it.toAbsolutePath().normalize().toString() }

            val compilation = compileOverlaySources(
                projectId = projectId,
                projectPath = resolvedProjectPath,
                sourceRoot = stagingSourceRoot,
                classesRoot = stagingClassesRoot
            )

            if (!compilation.success) {
                deleteRecursively(stagingRoot)
                return GeneratedFileSyncResponse(
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
            }

            replaceDirectory(stagingSourceRoot, sourceRoot)
            replaceDirectory(stagingClassesRoot, classesRoot)
            deleteRecursively(stagingRoot)

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
        } catch (e: Exception) {
            deleteRecursively(stagingRoot)
            logger.warn("Failed to sync overlay for {}: {}", projectId, e.message, e)
            GeneratedFileSyncResponse(
                success = false,
                projectId = projectId,
                projectPath = resolvedProjectPath,
                error = e.message ?: "Overlay sync failed"
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
        sourceRoot: Path,
        classesRoot: Path
    ): OverlayCompilationResult {
        val allSourceFiles = collectOverlaySources(sourceRoot)

        if (allSourceFiles.isEmpty()) {
            return OverlayCompilationResult(
                success = true,
                warnings = listOf("Overlay sources are empty; nothing to compile.")
            )
        }

        val javaFiles = allSourceFiles.filter { it.toString().endsWith(".java") }
        val kotlinFiles = allSourceFiles.filter { it.toString().endsWith(".kt") }

        return when {
            javaFiles.isNotEmpty() && kotlinFiles.isNotEmpty() ->
                compileMixedOverlay(projectId, projectPath, javaFiles, kotlinFiles, classesRoot)

            kotlinFiles.isNotEmpty() ->
                tryCompileSingleLanguage(
                    projectId = projectId,
                    language = "Kotlin",
                    classesRoot = classesRoot
                ) {
                    compileKotlin(allSourceFiles, classesRoot, projectPath)
                }

            else ->
                tryCompileSingleLanguage(
                    projectId = projectId,
                    language = "Java",
                    classesRoot = classesRoot
                ) {
                    compileJava(allSourceFiles, classesRoot, projectPath)
                }
        }
    }

    private fun compileMixedOverlay(
        projectId: String,
        projectPath: String,
        javaFiles: List<Path>,
        kotlinFiles: List<Path>,
        classesRoot: Path
    ): OverlayCompilationResult {
        val attempts = listOf(
            "java-first" to {
                clearDirectory(classesRoot)
                compileJava(javaFiles, classesRoot, projectPath)
                compileKotlin(
                    kotlinFiles,
                    classesRoot,
                    projectPath,
                    extraClasspath = classesRoot.toString()
                )
            },
            "kotlin-first" to {
                clearDirectory(classesRoot)
                compileKotlin(kotlinFiles, classesRoot, projectPath)
                compileJava(
                    javaFiles,
                    classesRoot,
                    projectPath,
                    extraClasspath = classesRoot.toString()
                )
            }
        )

        val failures = mutableListOf<String>()

        for ((name, attempt) in attempts) {
            try {
                attempt()
                return OverlayCompilationResult(
                    success = true,
                    compiledFiles = javaFiles.size + kotlinFiles.size,
                    warnings = listOf("Mixed Java/Kotlin overlay compiled using $name order.")
                )
            } catch (e: Exception) {
                failures += "[$name] ${e.message ?: "unknown error"}"
                logger.warn("Mixed overlay compile attempt {} failed for {}: {}", name, projectId, e.message)
            }
        }

        return OverlayCompilationResult(
            success = false,
            error = "Mixed Java/Kotlin overlay compilation failed in both orders:\n${failures.joinToString("\n")}",
            warnings = listOf(
                "Mixed Java/Kotlin overlay files were accepted, but compilation failed in both orders.",
                "If the files reference each other cyclically, split the change into smaller steps."
            )
        )
    }

    private fun tryCompileSingleLanguage(
        projectId: String,
        language: String,
        classesRoot: Path,
        block: () -> Unit
    ): OverlayCompilationResult {
        return try {
            clearDirectory(classesRoot)
            block()
            OverlayCompilationResult(success = true, compiledFiles = 0)
        } catch (e: Exception) {
            logger.warn("{} overlay compilation failed for {}: {}", language, projectId, e.message)
            OverlayCompilationResult(
                success = false,
                error = e.message ?: "$language overlay compilation failed"
            )
        }
    }

    private fun compileKotlin(
        sourceFiles: List<Path>,
        outputDir: Path,
        projectPath: String,
        extraClasspath: String? = null
    ) {
        val compiler = K2JVMCompiler()
        val effectiveClasspath = effectiveClasspath(projectPath, extraClasspath)

        val args = mutableListOf(
            "-d", outputDir.toString(),
            "-jvm-target", "17",
            "-no-stdlib",
            "-no-reflect"
        )

        if (effectiveClasspath.isNotBlank()) {
            args += listOf("-classpath", effectiveClasspath)
        }

        args += sourceFiles.map { it.toString() }

        val errStream = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errStream))

        try {
            val exitCode = compiler.exec(System.err, *args.toTypedArray())
            if (exitCode != ExitCode.OK) {
                val errorMsg = errStream.toString(Charsets.UTF_8)
                throw CompilationException("Kotlin overlay compilation failed:\n$errorMsg")
            }
        } finally {
            System.setErr(originalErr)
        }
    }

    private fun compileJava(
        sourceFiles: List<Path>,
        outputDir: Path,
        projectPath: String,
        extraClasspath: String? = null
    ) {
        Files.createDirectories(outputDir)

        val fileManager = javaCompiler.getStandardFileManager(null, null, null)
        try {
            val compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                sourceFiles.map { it.toFile() }
            )

            val effectiveClasspath = effectiveClasspath(projectPath, extraClasspath)

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

    private fun effectiveClasspath(projectPath: String, extraClasspath: String? = null): String {
        val runtimeEntries = RuntimeClasspathResolver.resolveRuntimeClasspathEntries().map { it.toString() }
        val projectClasspath = ProjectClasspathResolver.buildCompilationClasspath(projectPath)

        return ClasspathUtils.mergeClasspathStrings(
            runtimeEntries.joinToString(File.pathSeparator),
            projectClasspath,
            extraClasspath
        )
    }

    private fun clearDirectory(path: Path) {
        if (Files.exists(path)) {
            deleteRecursively(path)
        }
        Files.createDirectories(path)
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