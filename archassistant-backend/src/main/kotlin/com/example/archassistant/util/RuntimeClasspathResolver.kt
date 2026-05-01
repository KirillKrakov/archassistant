package com.example.archassistant.util

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

object RuntimeClasspathResolver {
    private val logger = LoggerFactory.getLogger(RuntimeClasspathResolver::class.java)
    private val extractedBootLibsCache = ConcurrentHashMap<Path, List<Path>>()

    fun resolveRuntimeClasspathEntries(): List<Path> {
        val entries = linkedSetOf<Path>()

        val rawClasspath = System.getProperty("java.class.path").orEmpty()
        ClasspathUtils.splitClasspathToPaths(rawClasspath).forEach { entry ->
            entries.add(entry)

            if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar", ignoreCase = true)) {
                entries.addAll(extractBootNestedJars(entry))
            }
        }

        val codeSourcePath = runCatching {
            val location = RuntimeClasspathResolver::class.java.protectionDomain?.codeSource?.location ?: return@runCatching null
            Paths.get(location.toURI())
        }.getOrNull()

        if (codeSourcePath != null) {
            entries.add(codeSourcePath)
            if (Files.isRegularFile(codeSourcePath) && codeSourcePath.toString().endsWith(".jar", ignoreCase = true)) {
                entries.addAll(extractBootNestedJars(codeSourcePath))
            }
        }

        return entries.toList()
    }

    private fun extractBootNestedJars(appJar: Path): List<Path> {
        return extractedBootLibsCache.computeIfAbsent(appJar.toAbsolutePath().normalize()) { jarPath ->
            val extractedDir = Files.createTempDirectory("archassistant-boot-libs-")
            val extracted = mutableListOf<Path>()

            try {
                ZipFile(jarPath.toFile()).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if (!entry.name.startsWith("BOOT-INF/lib/")) continue
                        if (!entry.name.endsWith(".jar")) continue

                        val fileName = Paths.get(entry.name).fileName.toString()
                        val outPath = extractedDir.resolve(fileName)

                        zip.getInputStream(entry).use { input ->
                            Files.copy(input, outPath, StandardCopyOption.REPLACE_EXISTING)
                        }

                        extracted.add(outPath)
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to extract nested jars from {}: {}", jarPath, e.message)
            }

            extracted
        }
    }
}