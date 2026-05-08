package com.example.archassistant.util

import com.example.archassistant.model.ProjectContextSnapshot
import org.slf4j.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object KotlinCompilationSupport {

    fun compileKotlin(
        sourceFiles: List<Path>,
        outputDir: Path,
        classpath: String,
        projectContext: ProjectContextSnapshot?,
        logger: Logger,
        operationLabel: String
    ) {
        Files.createDirectories(outputDir)

        val kotlinHome = resolveKotlinHome()
        val kotlinc = resolveKotlincExecutable(kotlinHome)
            ?: throw CompilationException(
                "$operationLabel Kotlin compilation failed: kotlinc not found. " +
                        "Set KOTLIN_HOME or put kotlinc on PATH."
            )

        val stdlibClasspath = kotlinHome?.let { resolveKotlinLibrariesClasspath(it) }.orEmpty()
        val backendRuntimeClasspath = BootClasspathResolver.resolveRuntimeClasspathString()

        val effectiveClasspath = ClasspathUtils.mergeClasspathStrings(
            classpath,
            projectContext?.compilationClasspath,
            backendRuntimeClasspath,
            stdlibClasspath
        )

        val command = mutableListOf<String>().apply {
            add(kotlinc.toAbsolutePath().toString())
            add("-d")
            add(outputDir.toString())
            add("-jvm-target")
            add("17")
            add("-no-stdlib")
            add("-no-reflect")
            if (effectiveClasspath.isNotBlank()) {
                add("-classpath")
                add(effectiveClasspath)
            }
            kotlinHome?.let {
                add("-kotlin-home")
                add(it.toAbsolutePath().toString())
            }
            sourceFiles.forEach { add(it.toString()) }
        }

        logger.debug("Running kotlinc: {}", command.joinToString(" "))

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw CompilationException(
                buildString {
                    append("$operationLabel Kotlin compilation failed (exitCode=$exitCode)")
                    if (output.isNotBlank()) {
                        append(":\n")
                        append(output)
                    }
                }
            )
        }
    }

    private fun resolveKotlinHome(): Path? {
        val candidates = listOfNotNull(
            System.getProperty("kotlin.home"),
            System.getenv("KOTLIN_HOME"),
            System.getenv("KOTLIN_COMPILER_HOME"),
            System.getProperty("user.home")?.let { "$it/.sdkman/candidates/kotlin/current" },
            "/opt/kotlin",
            "/usr/share/kotlin"
        )

        return candidates
            .mapNotNull { raw -> runCatching { Paths.get(raw) }.getOrNull() }
            .firstOrNull { Files.exists(it) }
    }

    private fun resolveKotlincExecutable(kotlinHome: Path?): Path? {
        val candidates = buildList {
            kotlinHome?.let {
                add(it.resolve("bin/kotlinc"))
                add(it.resolve("bin/kotlinc.sh"))
                add(it.resolve("bin/kotlinc.bat"))
            }
        }

        val existingCandidate = candidates.firstOrNull { Files.exists(it) && Files.isRegularFile(it) }
        if (existingCandidate != null) return existingCandidate

        return findExecutableOnPath("kotlinc")
            ?: findExecutableOnPath("kotlinc.sh")
    }

    private fun findExecutableOnPath(executable: String): Path? {
        val pathEnv = System.getenv("PATH").orEmpty()
        val pathEntries = pathEnv.split(File.pathSeparatorChar).filter { it.isNotBlank() }

        return pathEntries
            .mapNotNull { dir ->
                val candidate = Paths.get(dir, executable)
                if (Files.exists(candidate) && Files.isRegularFile(candidate)) candidate else null
            }
            .firstOrNull()
    }

    private fun resolveKotlinLibrariesClasspath(kotlinHome: Path): String {
        val libDir = kotlinHome.resolve("lib")
        if (!Files.isDirectory(libDir)) return ""

        return Files.list(libDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".jar") }
                .map { it.toString() }
                .toList()
                .joinToString(File.pathSeparator)
        }
    }
}