package com.example.archassistant.util

import com.example.archassistant.model.ProjectContextSnapshot

object ProjectImportNormalizer {

    private val packageRegex = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)\s*;?\s*$""")
    private val importRegex = Regex("""(?m)^\s*import\s+([A-Za-z0-9_.*]+)\s*;?\s*$""")
    private val declaredTypeRegex = Regex(
        """(?m)^\s*(?:public|protected|private|abstract|final|sealed|static|non-sealed|\s)*\b(?:class|interface|record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)"""
    )
    private val typeTokenRegex = Regex("""\b[A-Z][A-Za-z0-9_]*\b""")

    fun normalize(
        code: String,
        projectContext: ProjectContextSnapshot?,
        primaryTypeName: String? = null
    ): String {
        if (projectContext == null || code.isBlank()) return code

        val currentPackage = packageRegex.find(code)?.groupValues?.getOrNull(1)?.trim()?.trim('.')

        val existingImports = importRegex.findAll(code)
            .mapNotNull { match ->
                ProjectTypeNameResolver.normalizeTypeName(match.groupValues.getOrNull(1))
            }
            .toSet()

        val existingImportedSimpleNames = existingImports
            .filter { !it.endsWith(".*") }
            .associateBy { it.substringAfterLast('.') }

        val declaredTypes = linkedSetOf<String>().apply {
            primaryTypeName
                ?.let { ProjectTypeNameResolver.typeAliases(it) }
                ?.forEach { add(it) }

            declaredTypeRegex.findAll(code).forEach { match ->
                ProjectTypeNameResolver.typeAliases(match.groupValues.getOrNull(1))
                    .forEach { add(it) }
            }
        }

        val importIndex = ProjectTypeNameResolver.buildUniqueImportIndex(
            classes = projectContext.classes,
            currentPackage = currentPackage
        )

        val referencedTokens = typeTokenRegex.findAll(code)
            .map { it.value }
            .toSet()

        val importsToAdd = referencedTokens
            .asSequence()
            .filter { token -> token !in declaredTypes }
            .mapNotNull { token -> importIndex[token] }
            .filter { fqcn ->
                val normalizedCurrentPackage = currentPackage?.trim('.')
                normalizedCurrentPackage.isNullOrBlank() || !fqcn.startsWith("$normalizedCurrentPackage.")
            }
            .filter { fqcn ->
                val simpleName = fqcn.substringAfterLast('.')
                val existingFqcn = existingImportedSimpleNames[simpleName]
                existingFqcn == null || existingFqcn == fqcn
            }
            .filter { fqcn -> fqcn !in existingImports }
            .distinct()
            .sorted()
            .toList()

        if (importsToAdd.isEmpty()) return code

        return insertImports(code, importsToAdd)
    }

    private fun insertImports(code: String, fqcnImports: List<String>): String {
        val lines = code.lines().toMutableList()

        val packageIndex = lines.indexOfFirst { packageRegex.matches(it.trim()) }
        val lastImportIndex = lines.indexOfLast { importRegex.matches(it.trim()) }

        val importLines = fqcnImports.map { "import $it;" }

        return when {
            lastImportIndex >= 0 -> {
                val insertionIndex = lastImportIndex + 1
                lines.addAll(insertionIndex, importLines)
                lines.joinToString("\n")
            }

            packageIndex >= 0 -> {
                val insertionIndex = packageIndex + 1
                lines.addAll(insertionIndex, listOf("", *importLines.toTypedArray(), ""))
                lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n")
            }

            else -> buildString {
                append(importLines.joinToString("\n"))
                append("\n\n")
                append(code)
            }
        }
    }
}