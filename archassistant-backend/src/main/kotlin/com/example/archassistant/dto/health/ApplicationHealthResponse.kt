package com.example.archassistant.dto.health

data class ApplicationHealthResponse(
    val status: String,
    val version: String,
    val timestamp: String
)