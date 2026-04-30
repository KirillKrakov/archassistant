package com.example.archassistant.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Режим выбора классов/пакетов для правила
 */
enum class SelectorMode {
    PACKAGE,
    CLASS_TYPE,
    ANNOTATION
}

/**
 * Декларативное описание архитектурного правила
 * Хранится в YAML-конфиге и используется для валидации через ArchUnit
 */
data class ArchitecturalRule(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("type")
    val type: RuleType,

    @JsonProperty("from_package")
    val fromPackage: String,

    @JsonProperty("to_package")
    val toPackage: String? = null,

    @JsonProperty("to_packages")
    val toPackages: List<String>? = null,

    @JsonProperty("constraint")
    val constraint: ConstraintType,

    @JsonProperty("pattern")
    val pattern: String? = null,

    @JsonProperty("annotation")
    val annotation: String? = null,

    @JsonProperty("from_selector_mode")
    val fromSelectorMode: SelectorMode = SelectorMode.PACKAGE,

    @JsonProperty("to_selector_mode")
    val toSelectorMode: SelectorMode = SelectorMode.PACKAGE,

    @JsonProperty("from_class_type")
    val fromClassType: ClassType? = null,

    @JsonProperty("to_class_type")
    val toClassType: ClassType? = null,

    @JsonProperty("severity")
    val severity: Severity = Severity.WARNING,

    @JsonProperty("weight")
    val weight: Double = 1.0,

    @JsonProperty("enabled")
    val enabled: Boolean = true,

    @JsonProperty("suggested")
    val suggested: Boolean = false
) {
    /**
     * Проверка, применимо ли правило к данному пакету.
     * Для class-level / annotation-level правил возвращаем true,
     * потому что они не завязаны на package selector.
     */
    fun appliesToPackage(packageName: String): Boolean {
        if (fromSelectorMode != SelectorMode.PACKAGE) return true

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
    DEPENDENCY,
    NAMING_CONVENTION,
    ANNOTATION_CHECK,
    LAYER_ISOLATION,
    CUSTOM;

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
    NO_DEPENDENCY,
    MUST_DEPEND,
    NAMING_SUFFIX,
    NAMING_PREFIX,
    HAS_ANNOTATION,
    NO_ANNOTATION,
    CUSTOM;

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