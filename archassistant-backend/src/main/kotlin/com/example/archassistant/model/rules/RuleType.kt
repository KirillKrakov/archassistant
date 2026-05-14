package com.example.archassistant.model.rules

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class RuleType {
    DEPENDENCY,
    NAMING_CONVENTION,
    ANNOTATION_CHECK,
    LAYER_ISOLATION,
    CYCLE_CHECK,
    INHERITANCE_CHECK,
    INTERFACE_CHECK,
    MODIFIER_CHECK,
    METHOD_SIGNATURE_CHECK,
    FIELD_CHECK,
    EXCEPTION_CHECK,
    CUSTOM;

    companion object {
        private fun normalize(value: String): String =
            value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
                .uppercase()

        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): RuleType {
            val normalized = normalize(value)
            return entries.find { it.name.equals(normalized, ignoreCase = true) } ?: DEPENDENCY
        }
    }

    @JsonValue
    fun toValue(): String = name
}