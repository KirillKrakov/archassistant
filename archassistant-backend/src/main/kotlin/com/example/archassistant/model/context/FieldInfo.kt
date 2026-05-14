package com.example.archassistant.model.context

data class FieldInfo(
    val name: String,
    val type: String,
    val modifiers: List<String> = emptyList()
)