package com.example.archassistant.util

/**
 * Утилита для построения ArchUnit-совместимых паттернов из имён пакетов
 */
object PackagePatternBuilder {

    /**
     * Строит список паттернов для ArchUnit из списка имён пакетов.
     *
     * ArchUnit поддерживает:
     * - ".." — любой подпакет (например, "..service..*" = любой подпакет service)
     * - "*" — любые символы в пределах одного уровня (например, "com.example.*Service")
     * - НЕ поддерживает "|" для объединения паттернов
     *
     * @param packages Список полных имён пакетов
     * @return Список паттернов, каждый из которых валиден для ArchUnit
     */
    fun buildWildcardPatterns(packages: List<String>): List<String> {
        if (packages.isEmpty()) return listOf("..*")
        if (packages.size == 1) return listOf("${packages.first()}..*")

        val commonPrefix = findCommonPrefix(packages)

        return if (commonPrefix.isNotEmpty() && packages.all { it.startsWith(commonPrefix) }) {
            // Все пакеты в одной иерархии — используем общий префикс
            // Важно: если префикс заканчивается на ".", убираем её перед добавлением "..*"
            val prefix = if (commonPrefix.endsWith(".")) commonPrefix.dropLast(1) else commonPrefix
            listOf("$prefix..*")
        } else {
            // Разные иерархии — возвращаем отдельный паттерн для каждого пакета
            // (генерация нескольких правил обрабатывается в шаблоне)
            packages.map { "$it..*" }
        }
    }

    /**
     * Поиск общего префикса у списка строк
     */
    fun findCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        if (strings.size == 1) return strings.first()

        var prefix = strings.first()
        for (str in strings.drop(1)) {
            while (!str.startsWith(prefix) && prefix.isNotEmpty()) {
                prefix = prefix.dropLast(1)
            }
            if (prefix.isEmpty()) break
        }
        return prefix
    }
}