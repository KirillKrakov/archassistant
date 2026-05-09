package com.example.archassistant.util

enum class SourceLanguage(val extension: String) {
    JAVA("java"),
    KOTLIN("kt")
}

object SourceLanguageDetector {

    private val kotlinMarkers = listOf(
        "fun ",
        "val ",
        "var ",
        "data class",
        "sealed class",
        "sealed interface",
        "object ",
        "companion object",
        "override fun",
        "lateinit var",
        "by lazy",
        "when (",
        " ?: ",
        "?.",
        "!!",
        "::class",
        "as?",
        "is "
    )

    private val javaStrongMarkers = listOf(
        "public class ",
        "public interface ",
        "public enum ",
        "record ",
        "implements ",
        "extends ",
        "new ",
        "throws "
    )

    fun detect(code: String): SourceLanguage {
        val normalized = code.replace("\r\n", "\n")

        // Kotlin markers win, because Kotlin code may still contain `public class`
        // and imports that look Java-like.
        if (kotlinMarkers.any { normalized.contains(it) }) {
            return SourceLanguage.KOTLIN
        }

        if (javaStrongMarkers.any { normalized.contains(it) }) {
            return SourceLanguage.JAVA
        }

        // If there is a class declaration and no Kotlin markers, assume Java.
        return SourceLanguage.JAVA
    }

    fun isKotlin(code: String): Boolean = detect(code) == SourceLanguage.KOTLIN
    fun isJava(code: String): Boolean = detect(code) == SourceLanguage.JAVA
}