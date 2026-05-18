package com.example.archassistant.service.generatedfiles.parser

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