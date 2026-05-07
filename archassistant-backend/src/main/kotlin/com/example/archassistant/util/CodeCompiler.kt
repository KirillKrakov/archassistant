package com.example.archassistant.util

import com.example.archassistant.model.ProjectContextSnapshot
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

class CodeCompiler {
    private val logger = LoggerFactory.getLogger(CodeCompiler::class.java)
    private val javaCompiler = ToolProvider.getSystemJavaCompiler()
        ?: throw IllegalStateException("Java compiler not available. Use JDK, not JRE.")

    fun compileCode(
        code: String,
        className: String,
        classpath: String = "",
        projectContext: ProjectContextSnapshot? = null
    ): Path {
        val tempRoot = Files.createTempDirectory("archassistant-compile-")

        val sourceFiles = GeneratedSourceParser
            .parse(code, className)
            .ifEmpty {
                val cleaned = CodeCleaner.cleanCode(code)
                val extension = if (detectLanguage(cleaned)) "kt" else "java"
                listOf(
                    GeneratedSourceFile(
                        packageName = null,
                        className = className.substringAfterLast('.'),
                        extension = extension,
                        sourceCode = cleaned,
                        relativePath = "${className.substringAfterLast('.') }.$extension"
                    )
                )
            }
            .map { generated ->
                generated.copy(
                    sourceCode = ProjectImportNormalizer.normalize(
                        code = generated.sourceCode,
                        projectContext = projectContext,
                        primaryTypeName = generated.className
                    )
                )
            }

        val writtenFiles = sourceFiles.map { generated ->
            val target = tempRoot.resolve(generated.relativePath)
            target.parent?.let { Files.createDirectories(it) }
            Files.writeString(target, generated.sourceCode)
            target
        }

        val javaFiles = sourceFiles.filter { it.isJava() }.map { tempRoot.resolve(it.relativePath) }
        val kotlinFiles = sourceFiles.filter { it.isKotlin() }.map { tempRoot.resolve(it.relativePath) }

        return when {
            javaFiles.isNotEmpty() && kotlinFiles.isNotEmpty() -> {
                throw CompilationException("Mixed Java/Kotlin multi-file output is not supported yet")
            }

            kotlinFiles.isNotEmpty() -> compileKotlin(writtenFiles, tempRoot, classpath, projectContext)
            else -> compileJava(writtenFiles, tempRoot, classpath, projectContext)
        }
    }

    private fun detectLanguage(code: String): Boolean {
        val kotlinKeywords = listOf("fun ", "val ", "var ", "?:", "!!", "data class", "sealed class", "object ")
        val isKotlin = kotlinKeywords.any { code.contains(it) }
        if (!isKotlin && code.contains("class ") && !code.contains(";")) {
            return true
        }
        return isKotlin
    }

    private fun effectiveClasspath(
        classpath: String,
        projectContext: ProjectContextSnapshot?
    ): String {
        val runtimeEntries = RuntimeClasspathResolver.resolveRuntimeClasspathEntries().map { it.toString() }

        return ClasspathUtils.mergeClasspathStrings(
            runtimeEntries.joinToString(File.pathSeparator),
            classpath,
            projectContext?.compilationClasspath
        )
    }

    private fun compileKotlin(
        sourceFiles: List<Path>,
        tempRoot: Path,
        classpath: String,
        projectContext: ProjectContextSnapshot?
    ): Path {
        val outputDir = tempRoot.resolve("classes")
        Files.createDirectories(outputDir)

        val compiler = K2JVMCompiler()
        val effectiveClasspath = effectiveClasspath(classpath, projectContext)

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
            throw CompilationException("Kotlin compilation failed:\n$errorMsg")
        }

        return tempRoot
    }

    private fun compileJava(
        sourceFiles: List<Path>,
        tempRoot: Path,
        classpath: String,
        projectContext: ProjectContextSnapshot?
    ): Path {
        val outputDir = tempRoot.resolve("classes")
        Files.createDirectories(outputDir)

        val fileManager = javaCompiler.getStandardFileManager(null, null, null)
        try {
            val compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                sourceFiles.map { it.toFile() }
            )

            val effectiveClasspath = effectiveClasspath(classpath, projectContext)

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
                    if (details.isBlank()) "Java compilation failed" else "Java compilation failed:\n$details"
                )
            }

            return tempRoot
        } finally {
            fileManager.close()
        }
    }

    fun cleanup(tempRoot: Path) {
        try {
            tempRoot.toFile().deleteRecursively()
        } catch (e: Exception) {
            logger.warn("Failed to delete temp dir: ${e.message}")
        }
    }
}

class CompilationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)