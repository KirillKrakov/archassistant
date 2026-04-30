package com.example.archassistant.util

/**
 * Утилита для построения package wildcard-паттернов
 */
object PackagePatternBuilder {

    fun buildWildcardPatterns(packages: List<String>): List<String> {
        val normalized = packages
            .map { it.trim().trim('.') }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalized.isEmpty()) return emptyList()

        val commonRoot = commonPackagePrefix(normalized)
        return if (commonRoot.isNotBlank()) {
            listOf("$commonRoot..*")
        } else {
            normalized.map { "$it..*" }
        }
    }

    fun commonPackagePrefix(packages: List<String>): String {
        val normalized = packages
            .map { it.trim().trim('.') }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalized.isEmpty()) return ""
        if (normalized.size == 1) {
            return normalized.first().substringBeforeLast('.', "")
        }

        val commonSegments = normalized
            .map { it.split('.') }
            .reduce { acc, parts ->
                val max = minOf(acc.size, parts.size)
                var index = 0
                while (index < max && acc[index] == parts[index]) {
                    index++
                }
                acc.take(index)
            }

        return commonSegments.joinToString(".")
    }

    fun wildcardToRegex(pattern: String): Regex {
        return wildcardToRegexString(pattern).toRegex()
    }

    fun wildcardToRegexString(pattern: String): String {
        val subpackageWildcard = "__SUBPKG_WILDCARD__"
        return pattern
            .trim()
            .replace("**", ".*")
            .replace("..*", subpackageWildcard)
            .replace("*", "[^.]*")
            .replace(".", "\\.")
            .replace(subpackageWildcard, "(\\..*)?")
    }

    fun matches(pattern: String, packageName: String): Boolean {
        return wildcardToRegex(pattern).matches(packageName)
    }
}