package com.example.archassistant.model.rules

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class ConstraintType {
    NO_DEPENDENCY,
    MUST_DEPEND,
    NAMING_SUFFIX,
    NAMING_PREFIX,
    HAS_ANNOTATION,
    NO_ANNOTATION,

    NO_CYCLE,
    MAX_CYCLE_LENGTH,

    SHOULD_EXTEND,
    SHOULD_NOT_EXTEND,
    SHOULD_IMPLEMENT,
    SHOULD_NOT_IMPLEMENT,

    SHOULD_BE_PUBLIC,
    SHOULD_NOT_BE_PUBLIC,
    SHOULD_BE_FINAL,
    SHOULD_NOT_BE_FINAL,
    SHOULD_BE_ABSTRACT,
    SHOULD_NOT_BE_ABSTRACT,

    RETURN_TYPE,
    PARAMETER_COUNT,
    PARAMETER_TYPES,
    METHOD_VISIBILITY,
    METHOD_NAME_PATTERN,

    FIELD_TYPE,
    FIELD_VISIBILITY,
    FIELD_ANNOTATION,
    FIELD_NAME_PATTERN,

    SHOULD_ONLY_THROW,
    SHOULD_NOT_THROW,

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
        fun fromValue(value: String): ConstraintType {
            val normalized = normalize(value)
            return entries.find { it.name.equals(normalized, ignoreCase = true) } ?: NO_DEPENDENCY
        }
    }

    @JsonValue
    fun toValue(): String = name
}