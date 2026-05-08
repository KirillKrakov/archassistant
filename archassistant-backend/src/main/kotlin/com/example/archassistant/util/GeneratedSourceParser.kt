package com.example.archassistant.util

data class GeneratedSourceFile(
    val packageName: String?,
    val className: String,
    val extension: String,
    val sourceCode: String,
    val relativePath: String
) {
    fun isJava(): Boolean = extension.equals("java", ignoreCase = true)
    fun isKotlin(): Boolean = extension.equals("kt", ignoreCase = true)
}

object GeneratedSourceParser {

    private val packagePattern = Regex("""^\s*package\s+([A-Za-z_][\w.]*)\s*;?\s*$""")
    private val javaTypePattern = Regex(
        """(?m)^\s*(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(?:sealed\s+)?(?:non-sealed\s+)?(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)\b"""
    )
    private val kotlinTypePattern = Regex(
        """(?m)^\s*(?:public\s+)?(?:private\s+)?(?:protected\s+)?(?:internal\s+)?(?:data\s+)?(?:sealed\s+)?(?:abstract\s+)?(?:open\s+)?(?:inner\s+)?(?:class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)\b"""
    )

    fun parse(rawCode: String, fallbackClassName: String? = null): List<GeneratedSourceFile> {
        val cleaned = CodeCleaner.cleanCode(rawCode)
        if (cleaned.isBlank()) return emptyList()

        val candidateBlocks = extractCodeBlocks(cleaned).ifEmpty { listOf(cleaned) }

        return candidateBlocks
            .flatMap { block ->
                splitIntoPackageBlocks(block)
            }
            .mapNotNull { block ->
                val extension = SourceLanguageDetector.detect(block).extension
                buildSourceFile(
                    block = block,
                    extension = extension,
                    fallbackClassName = fallbackClassName
                )
            }
            .ifEmpty {
                buildFallbackSourceFile(
                    cleaned,
                    SourceLanguageDetector.detect(cleaned).extension,
                    fallbackClassName
                )?.let { listOf(it) }
                    ?: emptyList()
            }
    }

    private fun extractCodeBlocks(code: String): List<String> {
        val fencePattern = Regex("(?s)```(?:[a-zA-Z0-9_+-]+)?\\s*(.*?)```")
        val matches = fencePattern.findAll(code).mapNotNull { it.groupValues.getOrNull(1)?.trim() }.toList()
        return matches.filter { it.isNotBlank() }
    }

    private fun splitIntoPackageBlocks(code: String): List<String> {
        val lines = code.lines()
        val blocks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()

        for (line in lines) {
            val isPackageLine = packagePattern.matches(line)
            if (isPackageLine && current.isNotEmpty()) {
                blocks.add(current)
                current = mutableListOf()
            }
            current.add(line)
        }

        if (current.isNotEmpty()) {
            blocks.add(current)
        }

        return blocks
            .map { it.joinToString("\n").trim() }
            .filter { it.isNotBlank() }
    }

    private fun buildSourceFile(
        block: String,
        extension: String,
        fallbackClassName: String?
    ): GeneratedSourceFile? {
        val packageName = findPackageName(block)
        val className = inferClassName(block, fallbackClassName) ?: return null
        val relativePath = buildRelativePath(packageName, className, extension)

        return GeneratedSourceFile(
            packageName = packageName,
            className = className,
            extension = extension,
            sourceCode = block.trim(),
            relativePath = relativePath
        )
    }

    private fun buildFallbackSourceFile(
        code: String,
        extension: String,
        fallbackClassName: String?
    ): GeneratedSourceFile? {
        val className = inferClassName(code, fallbackClassName) ?: return null
        val relativePath = buildRelativePath(null, className, extension)

        return GeneratedSourceFile(
            packageName = null,
            className = className,
            extension = extension,
            sourceCode = code.trim(),
            relativePath = relativePath
        )
    }

    private fun findPackageName(block: String): String? {
        return block.lineSequence()
            .mapNotNull { line -> packagePattern.matchEntire(line)?.groupValues?.getOrNull(1) }
            .firstOrNull()
    }

    private fun inferClassName(code: String, fallbackClassName: String?): String? {
        return sequenceOf(javaTypePattern, kotlinTypePattern)
            .flatMap { pattern -> pattern.findAll(code).map { it.groupValues[1] } }
            .firstOrNull()
            ?: fallbackClassName?.substringAfterLast('.')
    }

    private fun buildRelativePath(
        packageName: String?,
        className: String,
        extension: String
    ): String {
        val packagePath = packageName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace('.', '/')
            ?.trim('/')

        return if (packagePath.isNullOrBlank()) {
            "$className.$extension"
        } else {
            "$packagePath/$className.$extension"
        }
    }
}