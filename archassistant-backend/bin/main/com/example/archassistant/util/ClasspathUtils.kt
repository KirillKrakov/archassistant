package com.example.archassistant.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ClasspathUtils {

    fun mergeClasspathStrings(vararg classpaths: String?): String {
        val entries = linkedSetOf<String>()

        classpaths
            .filterNotNull()
            .forEach { classpath ->
                if (classpath.isBlank()) return@forEach
                classpath
                    .split(File.pathSeparatorChar)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { entries.add(it) }
            }

        return entries.joinToString(File.pathSeparator)
    }

    fun splitClasspathToPaths(classpath: String?): List<Path> {
        if (classpath.isNullOrBlank()) return emptyList()

        return classpath
            .split(File.pathSeparatorChar)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                runCatching { Paths.get(entry) }.getOrNull()
                    ?.takeIf { Files.exists(it) }
            }
            .distinct()
    }

    fun splitClasspathToDirectories(classpath: String?): List<Path> {
        return splitClasspathToPaths(classpath)
            .filter { Files.isDirectory(it) }
            .distinct()
    }
}