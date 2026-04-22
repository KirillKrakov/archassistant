package com.example.archassistant.util

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider

class CodeCompiler {
    private val logger = LoggerFactory.getLogger(CodeCompiler::class.java)
    private val javaCompiler = ToolProvider.getSystemJavaCompiler()
        ?: throw IllegalStateException("Java compiler not available. Use JDK, not JRE.")

    fun compileCode(code: String, className: String, classpath: String = ""): Path {
        val tempRoot = Files.createTempDirectory("archassistant-compile-")
        val isKotlin = detectLanguage(code)
        val extension = if (isKotlin) "kt" else "java"
        val sourceFile = tempRoot.resolve("$className.$extension")
        Files.writeString(sourceFile, code)

        return if (isKotlin) {
            compileKotlin(sourceFile, tempRoot, classpath)
        } else {
            compileJava(sourceFile, tempRoot, classpath)
        }
    }

    private fun detectLanguage(code: String): Boolean {
        // Если код содержит Kotlin-специфичные ключевые слова, скорее всего это Kotlin
        val kotlinKeywords = listOf("fun ", "val ", "var ", "?:", "!!", "data class", "sealed class", "object ")
        val isKotlin = kotlinKeywords.any { code.contains(it) }
        // Также, если есть пакет и класс, но нет точки с запятой в конце строк (и не Java-стиль)
        if (!isKotlin && code.contains("class ") && !code.contains(";")) {
            return true
        }
        return isKotlin
    }

    private fun compileKotlin(sourceFile: Path, tempRoot: Path, classpath: String): Path {
        val outputDir = tempRoot.resolve("classes")
        Files.createDirectories(outputDir)

        val compiler = K2JVMCompiler()
        val args = mutableListOf(
            "-d", outputDir.toString(),
            "-classpath", classpath.ifEmpty { System.getProperty("java.class.path") },
            sourceFile.toString()
        )

        // Перенаправляем stderr для сбора ошибок
        val errStream = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errStream))

        val exitCode = try {
            // ВАЖНО: используем spread operator * для передачи массива как vararg
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

    private fun compileJava(sourceFile: Path, tempRoot: Path, classpath: String): Path {
        val outputDir = tempRoot.resolve("classes")
        Files.createDirectories(outputDir)

        val fileManager = javaCompiler.getStandardFileManager(null, null, null)
        val compilationUnits = fileManager.getJavaFileObjects(sourceFile.toFile())

        val options = mutableListOf("-d", outputDir.toString())
        if (classpath.isNotEmpty()) {
            options.addAll(listOf("-classpath", classpath))
        }

        val outWriter = OutputStreamWriter(System.out)
        val errWriter = OutputStreamWriter(System.err)

        val task = javaCompiler.getTask(
            outWriter, fileManager, null, options, null, compilationUnits
        )
        val success = task.call()
        fileManager.close()

        if (!success) {
            throw CompilationException("Java compilation failed")
        }
        return tempRoot
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