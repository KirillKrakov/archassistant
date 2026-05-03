package com.example.archassistant.model

import com.example.archassistant.util.PackagePatternBuilder
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue

enum class SelectorMode {
    PACKAGE,
    CLASS_TYPE,
    LAYER,
    ANNOTATION,
    MEMBER
}

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

    @JsonProperty("from_layer_type")
    val fromLayerType: LayerType? = null,

    @JsonProperty("to_layer_type")
    val toLayerType: LayerType? = null,

    @JsonProperty("from_name_pattern")
    val fromNamePattern: String? = null,

    @JsonProperty("to_name_pattern")
    val toNamePattern: String? = null,

    @JsonProperty("from_method_name_pattern")
    val fromMethodNamePattern: String? = null,

    @JsonProperty("to_method_name_pattern")
    val toMethodNamePattern: String? = null,

    @JsonProperty("from_field_name_pattern")
    val fromFieldNamePattern: String? = null,

    @JsonProperty("to_field_name_pattern")
    val toFieldNamePattern: String? = null,

    @JsonProperty("from_return_type")
    val fromReturnType: String? = null,

    @JsonProperty("to_return_type")
    val toReturnType: String? = null,

    @JsonProperty("from_parameter_types")
    val fromParameterTypes: List<String>? = null,

    @JsonProperty("to_parameter_types")
    val toParameterTypes: List<String>? = null,

    @JsonProperty("from_throws_types")
    val fromThrowsTypes: List<String>? = null,

    @JsonProperty("to_throws_types")
    val toThrowsTypes: List<String>? = null,

    @JsonProperty("from_modifiers")
    val fromModifiers: List<String>? = null,

    @JsonProperty("to_modifiers")
    val toModifiers: List<String>? = null,

    @JsonProperty("from_field_type")
    val fromFieldType: String? = null,

    @JsonProperty("to_field_type")
    val toFieldType: String? = null,

    @JsonProperty("slice_pattern")
    val slicePattern: String? = null,

    @JsonProperty("max_cycle_length")
    val maxCycleLength: Int? = null,

    @JsonProperty("severity")
    val severity: Severity = Severity.WARNING,

    @JsonProperty("weight")
    val weight: Double = 1.0,

    @JsonProperty("enabled")
    val enabled: Boolean = true,

    @JsonProperty("suggested")
    val suggested: Boolean = false
) {
    fun appliesToPackage(packageName: String): Boolean {
        if (fromSelectorMode != SelectorMode.PACKAGE) return true
        if (fromPackage.isBlank()) return false
        return PackagePatternBuilder.matches(fromPackage, packageName)
    }
}

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