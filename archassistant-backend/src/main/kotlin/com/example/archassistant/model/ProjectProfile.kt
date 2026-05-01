package com.example.archassistant.model

enum class ProjectProfile {
    SPRING_LAYERED,
    SPRING_FEATURED,
    CLEAN,
    HEXAGONAL,
    MVVM,
    MODULAR,
    UNKNOWN
}

data class ProjectProfileDetection(
    val primaryProfile: ProjectProfile,
    val confidence: Double,
    val scores: Map<ProjectProfile, Double>,
    val reasons: List<String>,
    val candidateProfiles: List<ProjectProfile> = emptyList(),
    val isConfident: Boolean = false
)