package com.example.archassistant.util

/**
 * Утилита для построения ArchUnit-совместимых паттернов из имён пакетов
 */
object PackagePatternBuilder {

    fun buildWildcardPatterns(packages: List<String>): List<String> {
        if (packages.isEmpty()) return listOf("..*")

        val normalized = packages
            .map { it.trim().trim('.') }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalized.isEmpty()) return listOf("..*")
        if (normalized.size == 1) return listOf("${normalized.first()}..*")

        val commonPrefix = commonPackagePrefix(normalized)
        return if (commonPrefix.isNotBlank()) {
            listOf("$commonPrefix..*")
        } else {
            normalized.map { "$it..*" }
        }
    }

    /**
     * Общий префикс пакетов, сегментно (а не по символам).
     */
    fun commonPackagePrefix(packages: List<String>): String {
        if (packages.isEmpty()) return ""
        if (packages.size == 1) return packages.first()

        val segments = packages.map { it.trim().trim('.').split('.') }
        val minSize = segments.minOf { it.size }
        val common = mutableListOf<String>()

        for (i in 0 until minSize) {
            val segment = segments.first()[i]
            if (segments.all { it[i] == segment }) {
                common += segment
            } else {
                break
            }
        }

        return common.joinToString(".")
    }

    /**
     * Возвращает один компактный wildcard-паттерн для списка пакетов.
     * Подходит для relation-based rules.
     */
    fun compactPattern(packages: List<String>): String? {
        val normalized = packages
            .map { it.trim().trim('.') }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalized.isEmpty()) return null
        if (normalized.size == 1) return "${normalized.first()}..*"

        val prefix = commonPackagePrefix(normalized)
        return if (prefix.isNotBlank()) {
            "$prefix..*"
        } else {
            "${normalized.first()}..*"
        }
    }

    fun buildRegex(pattern: String): Regex {
        return wildcardToRegex(pattern).toRegex()
    }

    fun matches(pattern: String, packageName: String): Boolean {
        val regex = wildcardToRegex(pattern).toRegex()
        return regex.matches(packageName)
    }

    private fun wildcardToRegex(pattern: String): String {
        val subpackageWildcard = "__SUBPKG_WILDCARD__"
        return pattern
            .replace("..*", subpackageWildcard)
            .replace("**", ".*")
            .replace("*", "[^.]*")
            .replace(".", "\\.")
            .replace(subpackageWildcard, "(\\..*)?")
    }
}