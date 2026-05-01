package com.example.archassistant.util

/**
 * Утилита для очистки сгенерированного кода от markdown-разметки и артефактов LLM
 */
object CodeCleaner {

    /**
     * Удаляет markdown code block markers и лишние символы из кода
     */
    fun cleanCode(rawCode: String): String {
        var code = rawCode.trim()

        // Удаляем открывающий маркер ```java, ```kotlin, ``` или <code>
        code = code.replaceFirst(Regex("^```(?:java|kotlin|scala|groovy)?\\s*"), "")
        code = code.replaceFirst(Regex("^<code>\\s*"), "")

        // Удаляем закрывающий маркер ``` или </code>
        code = code.replace(Regex("\\s*```$"), "")
        code = code.replace(Regex("\\s*</code>$"), "")

        // Удаляем возможные markdown-комментарии типа <!-- ... -->
        code = code.replace(Regex("<!--.*?-->"), "")

        // Удаляем лишние пустые строки в начале/конце
        return code.trim()
    }

    /**
     * Извлекает имя класса из кода (более надёжная версия)
     */
    fun extractClassName(code: String): String? {
        val cleaned = cleanCode(code)

        // Pattern для Java/Kotlin class declaration
        val pattern = Regex(
            """(?:public\s+|private\s+|protected\s+)?(?:abstract\s+|final\s+|sealed\s+|data\s+)?(?:class|interface|enum|record)\s+(\w+)"""
        )

        return pattern.find(cleaned)?.groupValues?.get(1)
    }
}