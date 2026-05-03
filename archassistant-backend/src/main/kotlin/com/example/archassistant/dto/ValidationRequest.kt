package com.example.archassistant.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ValidationRequest(
    val code: String,
    val className: String? = null,
    val projectId: String? = null,
    val classpath: String? = null,
    val rules: List<RuleDefinition>? = null
)

data class RuleDefinition(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val type: String = "dependency",
    @JsonProperty("from_package")
    val fromPackage: String,
    @JsonProperty("to_package")
    val toPackage: String? = null,
    @JsonProperty("to_packages")
    val toPackages: List<String>? = null,
    val constraint: String? = null,
    @JsonProperty("from_selector_mode")
    val fromSelectorMode: String? = null,
    @JsonProperty("to_selector_mode")
    val toSelectorMode: String? = null,
    @JsonProperty("from_class_type")
    val fromClassType: String? = null,
    @JsonProperty("to_class_type")
    val toClassType: String? = null,
    @JsonProperty("from_layer_type")
    val fromLayerType: String? = null,
    @JsonProperty("to_layer_type")
    val toLayerType: String? = null,
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
    @JsonProperty("pattern")
    val pattern: String? = null,
    val annotation: String? = null,
    @JsonProperty("slice_pattern")
    val slicePattern: String? = null,
    @JsonProperty("max_cycle_length")
    val maxCycleLength: Int? = null,
    val severity: String = "INFO",
    val weight: Double? = null,
    val enabled: Boolean = true
)