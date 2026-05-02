package com.example.archassistant.util

object CodeCleaner {

    private val fenceLine = Regex("""^\s*(```+|~~~+).*$""")
    private val packageLine = Regex("""^\s*package\s+[A-Za-z_][\w.]*\s*;?\s*$""")

    fun cleanCode(rawCode: String): String {
        val normalized = rawCode
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

        if (normalized.isBlank()) return normalized

        val withoutFences = normalized
            .lines()
            .filterNot { fenceLine.matches(it) }
            .joinToString("\n")
            .trim()

        if (withoutFences.isBlank()) return withoutFences

        val lines = withoutFences.lines()
        val firstPackageIndex = lines.indexOfFirst { packageLine.matches(it) }

        return if (firstPackageIndex >= 0) {
            lines.drop(firstPackageIndex).joinToString("\n").trim()
        } else {
            withoutFences
        }
    }
}