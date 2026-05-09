package com.example.archassistant.util

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.jar.JarFile
import kotlin.io.path.exists

object BootClasspathResolver {

    private val logger = LoggerFactory.getLogger(BootClasspathResolver::class.java)
    private val extractedCacheRoot: Path =
        Paths.get(System.getProperty("java.io.tmpdir"), "archassistant-boot-classpath-cache")

    @Volatile
    private var cachedEntries: List<Path>? = null

    fun resolveRuntimeEntries(): List<Path> {
        cachedEntries?.let { return it }

        synchronized(this) {
            cachedEntries?.let { return it }

            val entries = linkedSetOf<Path>()

            // 1) Plain classpath entries from java.class.path.
            resolveProcessClasspathEntries().forEach { entries.add(it) }

            // 2) Explicit jar candidates from env / command / code source.
            val bootJar = resolveBootJarCandidate()
            if (bootJar != null) {
                if (isBootJar(bootJar)) {
                    entries += extractBootNestedJars(bootJar)
                } else {
                    logger.warn("Resolved app jar is not a Spring Boot jar: {}", bootJar)
                }
            } else {
                logger.warn(
                    "Unable to resolve Spring Boot app jar. " +
                            "Set ARCHASSISTANT_APP_JAR or APP_JAR if extraction is needed."
                )
            }

            val resolved = entries
                .filter { Files.exists(it) }
                .distinctBy { it.toAbsolutePath().normalize().toString() }

            cachedEntries = resolved
            return resolved
        }
    }

    fun resolveRuntimeClasspathString(): String =
        resolveRuntimeEntries().joinToString(File.pathSeparator) { it.toString() }

    private fun resolveProcessClasspathEntries(): List<Path> {
        val rawClasspath = System.getProperty("java.class.path").orEmpty()
        if (rawClasspath.isBlank()) return emptyList()

        return rawClasspath
            .split(File.pathSeparatorChar)
            .mapNotNull { raw ->
                val trimmed = raw.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                runCatching { Paths.get(trimmed) }.getOrNull()
            }
            .filter { Files.exists(it) }
    }

    private fun resolveBootJarCandidate(): Path? {
        val envCandidates = listOfNotNull(
            System.getenv("ARCHASSISTANT_APP_JAR"),
            System.getenv("APP_JAR"),
            System.getenv("JAR_FILE")
        )

        val commandCandidate = resolveFromJavaCommand()
        val codeSourceCandidate = resolveCodeSourceJar()
        val classPathCandidates = resolveProcessClasspathEntries().filter { it.toString().endsWith(".jar") }

        val allCandidates = buildList {
            envCandidates.forEach { add(it) }
            commandCandidate?.let { add(it.toString()) }
            codeSourceCandidate?.let { add(it.toString()) }
            classPathCandidates.forEach { add(it.toString()) }
        }

        return allCandidates
            .mapNotNull { raw -> runCatching { Paths.get(raw) }.getOrNull() }
            .firstOrNull { Files.exists(it) && Files.isRegularFile(it) && it.toString().endsWith(".jar") }
    }

    private fun resolveFromJavaCommand(): Path? {
        val command = System.getProperty("sun.java.command").orEmpty().trim()
        if (command.isBlank()) return null

        val firstToken = command.split(' ', '\t').firstOrNull().orEmpty().trim()
        if (!firstToken.endsWith(".jar", ignoreCase = true)) return null

        return runCatching { Paths.get(firstToken) }.getOrNull()?.takeIf { Files.exists(it) }
            ?: runCatching { Paths.get("/app").resolve(firstToken) }.getOrNull()?.takeIf { Files.exists(it) }
    }

    private fun resolveCodeSourceJar(): Path? {
        return runCatching {
            val location = BootClasspathResolver::class.java.protectionDomain.codeSource?.location ?: return null
            val uri = location.toURI()
            val path = Paths.get(uri)
            if (Files.exists(path) && Files.isRegularFile(path) && path.toString().endsWith(".jar")) path else null
        }.getOrNull()
    }

    private fun isBootJar(jar: Path): Boolean {
        return runCatching {
            JarFile(jar.toFile()).use { jf ->
                jf.entries().asSequence().any { it.name.startsWith("BOOT-INF/lib/") && it.name.endsWith(".jar") }
            }
        }.getOrDefault(false)
    }

    private fun extractBootNestedJars(bootJar: Path): List<Path> {
        val digest = sha256("${bootJar.toAbsolutePath().normalize()}|${bootJar.toFile().length()}|${bootJar.toFile().lastModified()}")
        val targetDir = extractedCacheRoot.resolve(digest)

        if (Files.isDirectory(targetDir)) {
            val existing = Files.list(targetDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".jar") }.toList()
            }
            if (existing.isNotEmpty()) return existing
        }

        Files.createDirectories(targetDir)

        val extracted = mutableListOf<Path>()

        JarFile(bootJar.toFile()).use { jar ->
            val libEntries = jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("BOOT-INF/lib/") && it.name.endsWith(".jar") }
                .toList()

            if (libEntries.isEmpty()) {
                logger.warn("No BOOT-INF/lib jars found in {}", bootJar)
                return emptyList()
            }

            libEntries.forEach { entry ->
                val fileName = entry.name.substringAfterLast('/')
                val targetFile = targetDir.resolve(fileName)
                jar.getInputStream(entry).use { input ->
                    Files.newOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                extracted.add(targetFile)
            }
        }

        logger.info("Extracted {} nested Spring Boot jars to {}", extracted.size, targetDir)
        return extracted
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(value.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}