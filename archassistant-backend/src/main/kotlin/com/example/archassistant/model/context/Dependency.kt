package com.example.archassistant.model.context

data class Dependency(
    val from: String,
    val to: String,
    val type: DependencyType = DependencyType.IMPORT
)