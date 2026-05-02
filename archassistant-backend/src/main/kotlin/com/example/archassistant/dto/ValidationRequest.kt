package com.example.archassistant.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonNaming

data class ValidationRequest(
    val code: String,                              // Исходный код для валидации
    val className: String? = null,                 // Имя класса (опционально: извлечём из кода)
    val projectId: String? = null,                 // ID проекта для загрузки правил из репозитория
    val classpath: String? = null,                 // Classpath для компиляции при валидации
    val rules: List<RuleDefinition>? = null        // Явные правила (переопределяют projectId)
)

data class RuleDefinition(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val type: String = "noDependency",
    @JsonProperty("from_package")
    val fromPackage: String,
    @JsonProperty("to_package")
    val toPackage: String? = null,
    @JsonProperty("to_packages")
    val toPackages: List<String>? = null,
    val constraint: String? = null,
    @JsonProperty("from_selector_mode")
    val fromSelectorMode: String? = null,
    @JsonProperty("from_class_type")
    val fromClassType: String? = null,
    @JsonProperty("to_class_type")
    val toClassType: String? = null,
    val pattern: String? = null,
    val annotation: String? = null,
    val severity: String = "INFO",
    val weight: Double? = null,
    val enabled: Boolean = true
)