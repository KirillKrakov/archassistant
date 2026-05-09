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
    val moduleRoots: List<String> = emptyList(),
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

    fun preferredLanguageHint(): String? {
        val candidateRoots = buildList {
            add(projectPath)
            addAll(moduleRoots)
        }
            .mapNotNull { runCatching { Paths.get(it) }.getOrNull() }
            .distinct()

        fun hasSourceRoot(root: Path, languageDir: String): Boolean {
            val candidate = root
                .resolve("src")
                .resolve("main")
                .resolve(languageDir)
            return Files.exists(candidate)
        }

        val hasJava = candidateRoots.any { hasSourceRoot(it, "java") }
        val hasKotlin = candidateRoots.any { hasSourceRoot(it, "kotlin") }

        return when {
            hasJava && hasKotlin -> "Java/Kotlin"
            hasJava -> "Java"
            hasKotlin -> "Kotlin"
            else -> null
        }
    }

    fun mergedClasspath(extraClasspath: String? = null): String =
        ClasspathUtils.mergeClasspathStrings(compilationClasspath, extraClasspath)

    fun importPaths(): List<Path> =
        classesDirPaths
            .mapNotNull { runCatching { Paths.get(it) }.getOrNull() }
            .filter { Files.isDirectory(it) }
            .distinct()

    fun promptContext(
        requestText: String? = null,
        targetPackage: String? = null,
        expectedClassName: String? = null,
        existingTypes: Collection<String> = emptyList(),
        maxPackages: Int = 20,
        maxPriorityClasses: Int = 16,
        maxClassesPerLayer: Int = 8,
        maxDependencies: Int = 20,
        maxClassSignatures: Int = 24,
        maxFieldsPerClass: Int = 8,
        maxConstructorsPerClass: Int = 4
    ): String {
        val profileText = detection?.let {
            "${it.primaryProfile.name} (confidence ${"%.2f".format(it.confidence)})"
        } ?: "unknown"

        val sourceLanguage = preferredLanguageHint() ?: "mixed/unknown"
        val packageText = packages.sorted().take(maxPackages).joinToString(", ").ifBlank { "none" }
        val featureRootsText = structure.featureRoots(basePackage).take(10).joinToString(", ").ifBlank { "none" }
        val moduleRootsText = moduleRoots.take(12).joinToString(", ").ifBlank { "none" }

        val request = requestText.orEmpty()
        val focusClassNames = resolveFocusClassNames(request, expectedClassName, existingTypes)
        val focusPackage = resolveFocusPackage(request, targetPackage)

        val rankedClasses = classes.sortedWith(
            compareByDescending<ClassInfo> { relevanceScore(it, request, focusClassNames, focusPackage) }
                .thenBy { it.fullName }
        )

        val focusClasses = rankedClasses
            .filter { info ->
                info.simpleName in focusClassNames ||
                        (focusPackage != null && (info.packageName == focusPackage || info.packageName.startsWith("$focusPackage.")))
            }
            .distinctBy { it.fullName }
            .take(maxPriorityClasses)

        val focusClassNamesSet = focusClasses.map { it.fullName }.toSet()
        val remainingClasses = rankedClasses
            .filterNot { it.fullName in focusClassNamesSet }
            .take(maxClassSignatures)

        val focusContractsText = renderClassContracts(
            classes = focusClasses,
            maxFieldsPerClass = maxFieldsPerClass,
            maxConstructorsPerClass = maxConstructorsPerClass
        )

        val classContractsText = renderClassContracts(
            classes = remainingClasses,
            maxFieldsPerClass = maxFieldsPerClass,
            maxConstructorsPerClass = maxConstructorsPerClass
        )

        val layerText = structure
            .effectiveLayerMap()
            .entries
            .filter { it.value.isNotEmpty() }
            .joinToString("\n") { (layer, infos) ->
                val visible = infos
                    .sortedWith(
                        compareByDescending<ClassInfo> { relevanceScore(it, request, focusClassNames, focusPackage) }
                            .thenBy { it.fullName }
                    )
                    .take(maxClassesPerLayer)

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

        val importHintsText = if (focusClasses.isNotEmpty()) {
            focusClasses.joinToString("\n") { info ->
                "- import ${info.fullName}"
            }
        } else {
            "- none"
        }

        val requestSummaryText = buildString {
            appendLine("requestedFocus:")
            appendLine("  - expectedClassName: ${expectedClassName ?: "none"}")
            appendLine("  - targetPackage: ${focusPackage ?: targetPackage ?: "none"}")
            appendLine("  - referencedTypes: ${if (focusClassNames.isNotEmpty()) focusClassNames.joinToString(", ") else "none"}")
        }.trimEnd()

        return """
            PROJECT CONTEXT
            - projectId: $projectId
            - projectPath: $projectPath
            - moduleRoots: $moduleRootsText
            - sourceLanguage: $sourceLanguage
            - basePackage: ${if (basePackage.isBlank()) "unknown" else basePackage}
            - architecturePattern: ${architecturePattern?.name ?: "unknown"}
            - detectedProfile: $profileText
            - knownPackages: $packageText
            - featureRoots: $featureRootsText

            $requestSummaryText

            namingConventions:
              - controllerSuffix: ${namingConventions.controllerSuffix}
              - serviceSuffix: ${namingConventions.serviceSuffix}
              - repositorySuffix: ${namingConventions.repositorySuffix}
              - dtoSuffix: ${namingConventions.dtoSuffix}

            importHintsForCurrentRequest:
            $importHintsText

            requestRelevantTypeContracts:
            $focusContractsText

            existingClassesByLayer:
            $layerText

            classContracts:
            $classContractsText

            importantDependencies:
            $dependencyText

            INSTRUCTIONS FOR GENERATION:
            - Use only packages that exist in knownPackages or are the base package root.
            - Prefer existing classes and exact public method signatures from this context.
            - Never invent repository/service/controller types if a matching class is already present in the project.
            - If a request references a type from another package, import it explicitly.
            - For simple immutable DTO/value objects in Java 16+, prefer record unless the prompt clearly asks for a bean style.
            - Do not add extra constructor parameters or fields beyond the contract shown in requestRelevantTypeContracts.
        """.trimIndent()
    }

    private fun resolveFocusClassNames(
        requestText: String,
        expectedClassName: String?,
        existingTypes: Collection<String>
    ): Set<String> {
        val result = linkedSetOf<String>()

        expectedClassName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.substringAfterLast('.')
            ?.let { result.add(it) }

        existingTypes
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.substringAfterLast('.') }
            .forEach { result.add(it) }

        classes.asSequence()
            .map { it.simpleName }
            .distinct()
            .filter { simpleName -> requestText.contains(simpleName, ignoreCase = true) }
            .forEach { result.add(it) }

        return result
    }

    private fun resolveFocusPackage(requestText: String, targetPackage: String?): String? {
        targetPackage
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.trim('.')
            ?.let { return it }

        return packages
            .asSequence()
            .sortedByDescending { it.length }
            .firstOrNull { pkg -> requestText.contains(pkg, ignoreCase = true) }
            ?.trim('.')
    }

    private fun relevanceScore(
        info: ClassInfo,
        requestText: String,
        focusClassNames: Set<String>,
        focusPackage: String?
    ): Int {
        var score = 0

        if (info.origin == ClassOrigin.OVERLAY) score += 1000
        if (info.simpleName in focusClassNames) score += 800
        if (focusPackage != null) {
            if (info.packageName == focusPackage) score += 500
            if (info.packageName.startsWith("$focusPackage.")) score += 250
        }

        val request = requestText.lowercase()
        if (request.contains("dto") && info.simpleName.endsWith("Dto")) score += 30
        if (request.contains("validator") && info.packageName.contains(".validation")) score += 30
        if (request.contains("service") && info.packageName.contains(".service")) score += 20
        if (request.contains("controller") && info.packageName.contains("controller")) score += 20
        if (request.contains("repository") && info.packageName.contains("repository")) score += 20
        if (info.kind == ClassKind.INTERFACE) score += 10
        if (info.kind == ClassKind.ANNOTATION) score += 5

        val occurrenceIndex = focusClassNames
            .mapNotNull { name -> requestText.indexOf(name, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull()

        if (occurrenceIndex != null) {
            score += (500 - occurrenceIndex.coerceAtMost(500))
        }

        return score
    }

    private fun renderClassContracts(
        classes: List<ClassInfo>,
        maxFieldsPerClass: Int,
        maxConstructorsPerClass: Int
    ): String {
        if (classes.isEmpty()) return "- none"

        return classes.joinToString("\n") { info ->
            renderClassContract(info, maxFieldsPerClass, maxConstructorsPerClass)
        }
    }

    private fun renderClassContract(
        info: ClassInfo,
        maxFieldsPerClass: Int,
        maxConstructorsPerClass: Int
    ): String {
        val modifiersText = if (info.modifiers.isNotEmpty()) info.modifiers.joinToString(" ") else "none"
        val superClassText = info.superClass ?: "none"
        val interfacesText = if (info.interfaces.isNotEmpty()) info.interfaces.joinToString(", ") else "none"
        val fieldsText = if (info.fields.isEmpty()) {
            "    - none"
        } else {
            info.fields.take(maxFieldsPerClass).joinToString("\n") { field ->
                val fieldModifiers = if (field.modifiers.isNotEmpty()) field.modifiers.joinToString(" ") + " " else ""
                "    - ${fieldModifiers}${field.type} ${field.name}"
            }
        }
        val constructorsText = if (info.constructors.isEmpty()) {
            "    - none"
        } else {
            info.constructors.take(maxConstructorsPerClass).joinToString("\n") { ctor ->
                val ctorModifiers = if (ctor.modifiers.isNotEmpty()) ctor.modifiers.joinToString(" ") + " " else ""
                val params = ctor.parameters.joinToString(", ")
                "    - ${ctorModifiers}${info.simpleName}($params)"
            }
        }
        val methodsText = if (info.publicMethods.isEmpty()) {
            "    - none"
        } else {
            info.publicMethods.take(10).joinToString("\n") { sig -> "    - $sig" }
        }

        return """
            - ${info.fullName} [${info.kind.name.lowercase()}, ${info.origin.name.lowercase()}]
              package: ${info.packageName}
              modifiers: $modifiersText
              superClass: $superClassText
              interfaces: $interfacesText
              fields:
            $fieldsText
              constructors:
            $constructorsText
              publicMethods:
            $methodsText
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