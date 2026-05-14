package com.example.archassistant.service.context.classpath

import com.example.archassistant.util.classpath.ClasspathUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ProjectClasspathResolver {

    fun resolveProjectOutputDirectories(projectPath: String): List<Path> {
        val root = Paths.get(projectPath)
        val projectName = root.fileName?.toString()

        val candidates = buildList {
            add(root.resolve("build/classes/kotlin/main"))
            add(root.resolve("build/classes/java/main"))
            add(root.resolve("build/resources/main"))
            add(root.resolve("target/classes"))

            if (!projectName.isNullOrBlank()) {
                add(root.resolve("out/production").resolve(projectName))
            }
        }

        return candidates
            .filter { Files.isDirectory(it) }
            .distinct()
    }

    fun buildCompilationClasspath(projectPath: String, includeRuntimeClasspath: Boolean = true): String {
        val entries = mutableListOf<String>()

        if (includeRuntimeClasspath) {
            entries += ClasspathUtils.splitClasspathToPaths(System.getProperty("java.class.path"))
                .map { it.toString() }
        }

        entries += resolveProjectOutputDirectories(projectPath).map { it.toString() }

        return ClasspathUtils.mergeClasspathStrings(*entries.toTypedArray())
    }
}