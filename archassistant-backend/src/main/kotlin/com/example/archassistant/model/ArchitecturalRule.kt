package com.example.archassistant.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Декларативное описание архитектурного правила
 * Хранится в YAML-конфиге и используется для валидации через ArchUnit
 */
data class ArchitecturalRule(
    val id: String,
    val name: String,
    val description: String? = null,
    val type: RuleType,
    val fromPackage: String,
    val toPackage: String? = null,
    val toPackages: List<String>? = null,
    val constraint: ConstraintType,
    val pattern: String? = null,           // Для naming_convention
    val annotation: String? = null,        // Для annotation_check
    val severity: Severity = Severity.WARNING,
    val weight: Double = 1.0,              // Вес для расчёта ComplianceScore
    val enabled: Boolean = true,
    val suggested: Boolean = false         // Помечает правила, предложенные системой
) {
    /**
     * Проверка, применимо ли правило к данному пакету
     */
    fun appliesToPackage(packageName: String): Boolean {
        val regex = fromPackage.toRegexPattern().toRegex()
        return regex.matches(packageName)
    }

    /**
     * Конвертация wildcard-паттерна в regex
     */
    private fun String.toRegexPattern(): String {
        val subpackageWildcard = "__SUBPKG_WILDCARD__"
        return this
            .replace("**", ".*")
            .replace("..*", subpackageWildcard)
            .replace("*", "[^.]*")
            .replace(".", "\\.")
            .replace(subpackageWildcard, "(\\..*)?")
    }
}

/**
 * Тип архитектурного правила
 */
enum class RuleType {
    DEPENDENCY,           // Зависимости между пакетами
    NAMING_CONVENTION,    // Именование классов/пакетов
    ANNOTATION_CHECK,     // Наличие/отсутствие аннотаций
    LAYER_ISOLATION,      // Изоляция слоёв
    CUSTOM;               // Пользовательское правило

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): RuleType {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: DEPENDENCY
        }
    }

    @JsonValue
    fun toValue(): String = name
}

/**
 * Тип ограничения в правиле
 */
enum class ConstraintType {
    NO_DEPENDENCY,        // Запрет зависимостей
    MUST_DEPEND,          // Обязательная зависимость
    NAMING_SUFFIX,        // Требуемый суффикс имени
    NAMING_PREFIX,        // Требуемый префикс имени
    HAS_ANNOTATION,       // Требуемая аннотация
    NO_ANNOTATION,        // Запрещённая аннотация
    CUSTOM;               // Пользовательское ограничение

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): ConstraintType {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: NO_DEPENDENCY
        }
    }

    @JsonValue
    fun toValue(): String = name
}