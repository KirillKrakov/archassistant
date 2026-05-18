package com.example.archassistant.model.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Уровень серьёзности нарушения
 */
enum class Severity {
    INFO,      // Информационное, не блокирует
    WARNING,   // Предупреждение, рекомендуется исправить
    ERROR,     // Ошибка, требует исправления
    CRITICAL;  // Критическая ошибка, блокирует принятие кода

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): Severity {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: WARNING
        }
    }

    @JsonValue
    fun toValue(): String = name
}