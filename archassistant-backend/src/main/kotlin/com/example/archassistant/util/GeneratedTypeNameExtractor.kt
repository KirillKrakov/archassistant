package com.example.archassistant.util

object GeneratedTypeNameExtractor {

    private val topLevelTypePattern = Regex(
        """(?m)^\s*(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(?:sealed\s+)?(?:non-sealed\s+)?(?:data\s+)?(?:class|interface|enum|record|object)\s+([A-Za-z_][A-Za-z0-9_]*)\b"""
    )

    fun extract(code: String): String? {
        return topLevelTypePattern.find(code)?.groupValues?.getOrNull(1)
    }
}