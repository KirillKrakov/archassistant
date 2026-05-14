package com.example.archassistant.model.context

data class ProjectProfileDetection(
    val primaryProfile: ProjectProfile,
    val confidence: Double,
    val scores: Map<ProjectProfile, Double>,
    val reasons: List<String>,
    val candidateProfiles: List<ProjectProfile> = emptyList(),
    val isConfident: Boolean = false
)