package com.example.archassistant.model

import com.example.archassistant.util.ClasspathUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

data class ProjectContextSnapshot(
    val projectId: String,
    val projectPath: String,
    val structure: ProjectStructure,
    val classesDirPaths: List<String> = emptyList(),
    val compilationClasspath: String = "",
    val createdAt: String = LocalDateTime.now().toString()
) {
    val architecturePattern: ArchitecturePattern?
        get() = structure.architecturePattern

    val detection: ProjectProfileDetection?
        get() = structure.detection

    val packages: List<String>
        get() = structure.packages

    val classes: List<ClassInfo>
        get() = structure.classes

    val namingConventions: NamingConventions
        get() = structure.namingConventions

    val dependencies: List<Dependency>
        get() = structure.dependencies

    val basePackage: String
        get() = detectBasePackage(structure.packages)

    fun mergedClasspath(extraClasspath: String? = null): String =
        ClasspathUtils.mergeClasspathStrings(compilationClasspath, extraClasspath)

    fun importPaths(): List<Path> =
        classesDirPaths
            .mapNotNull { runCatching { Paths.get(it) }.getOrNull() }
            .filter { Files.isDirectory(it) }
            .distinct()

    fun promptContext(
        maxPackages: Int = 20,
        maxClassesPerLayer: Int = 8,
        maxDependencies: Int = 20,
        maxClassSignatures: Int = 20
    ): String {
        val profileText = detection?.let {
            "${it.primaryProfile.name} (confidence ${"%.2f".format(it.confidence)})"
        } ?: "unknown"

        val packageText = packages
            .sorted()
            .take(maxPackages)
            .joinToString(", ")
            .ifBlank { "none" }

        val featureRootsText = structure
            .featureRoots(basePackage)
            .take(10)
            .joinToString(", ")
            .ifBlank { "none" }

        val layerText = structure
            .effectiveLayerMap()
            .entries
            .filter { it.value.isNotEmpty() }
            .joinToString("\n") { (layer, infos) ->
                val visible = infos.take(maxClassesPerLayer)
                val names = visible.joinToString(", ") { it.fullName }
                val remaining = infos.size - visible.size
                val suffix = if (remaining > 0) " … (+$remaining more)" else ""
                "- ${layer.name.lowercase()}: $names$suffix"
            }
            .ifBlank { "- none" }

        val dependencyText = dependencies
            .take(maxDependencies)
            .joinToString("\n") { dep ->
                "- ${dep.from} -> ${dep.to} [${dep.type.name.lowercase()}]"
            }
            .ifBlank { "- none" }

        val classSignatureText = classes
            .take(maxClassSignatures)
            .joinToString("\n") { info ->
                val methods = info.publicMethods.take(8)
                if (methods.isEmpty()) {
                    "- ${info.fullName}"
                } else {
                    "- ${info.fullName}\n  methods:\n${methods.joinToString("\n") { sig -> "    - $sig" }}"
                }
            }
            .ifBlank { "- none" }

        val absentRoots = listOf("repository", "repositories", "vaccination", "vaccinations")
            .filter { root -> packages.none { pkg -> pkg.contains(".$root.") || pkg.endsWith(".$root") || pkg == "$basePackage.$root" } }

        val absentRootsText = absentRoots.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"

        return """
            PROJECT CONTEXT
            - projectId: $projectId
            - projectPath: $projectPath
            - basePackage: ${if (basePackage.isBlank()) "unknown" else basePackage}
            - architecturePattern: ${architecturePattern?.name ?: "unknown"}
            - detectedProfile: $profileText
            - knownPackages: $packageText
            - missingCommonRoots: $absentRootsText
            - featureRoots: $featureRootsText

            namingConventions:
              - controllerSuffix: ${namingConventions.controllerSuffix}
              - serviceSuffix: ${namingConventions.serviceSuffix}
              - repositorySuffix: ${namingConventions.repositorySuffix}
              - dtoSuffix: ${namingConventions.dtoSuffix}

            existingClassesByLayer:
            $layerText

            existingClassSignatures:
            $classSignatureText

            importantDependencies:
            $dependencyText

            INSTRUCTIONS FOR GENERATION:
            - Use only packages that exist in knownPackages or are the base package root.
            - Do not invent package roots such as repository, repositories, or vaccination if they are not present in this project.
            - Prefer existing classes and exact public method signatures from this context.
            - If a method signature is shown here, use its real return type exactly as written.
        """.trimIndent()
    }

    private fun detectBasePackage(packages: List<String>): String {
        val tokens = packages
            .map { it.trim().trim('.') }
            .filter { it.isNotBlank() }
            .map { it.split('.') }

        if (tokens.isEmpty()) return ""

        var prefix = tokens.first()
        for (parts in tokens.drop(1)) {
            val limit = minOf(prefix.size, parts.size)
            var i = 0
            while (i < limit && prefix[i] == parts[i]) i++
            prefix = prefix.take(i)
            if (prefix.isEmpty()) break
        }

        return prefix.joinToString(".")
    }
}