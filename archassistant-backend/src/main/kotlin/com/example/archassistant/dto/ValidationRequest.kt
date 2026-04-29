package com.example.archassistant.dto

data class ValidationRequest(
    val code: String,                              // Исходный код для валидации
    val className: String? = null,                 // Имя класса (опционально: извлечём из кода)
    val projectId: String? = null,                 // ID проекта для загрузки правил из репозитория
    val classpath: String? = null,                 // Classpath для компиляции при валидации
    val rules: List<RuleDefinition>? = null        // Явные правила (переопределяют projectId)
)

data class RuleDefinition(
    val fromPackage: String,
    val toPackage: String,
    val toPackages: List<String>? = null,
    val type: String = "noDependency"
)