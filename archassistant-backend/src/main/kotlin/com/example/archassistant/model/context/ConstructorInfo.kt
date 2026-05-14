package com.example.archassistant.model.context

data class ConstructorInfo(
    val parameters: List<String> = emptyList(),
    val modifiers: List<String> = emptyList()
)