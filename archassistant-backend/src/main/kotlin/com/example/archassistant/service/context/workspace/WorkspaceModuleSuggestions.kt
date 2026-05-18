package com.example.archassistant.service.context.workspace

import com.example.archassistant.model.context.ProjectProfileDetection
import com.example.archassistant.model.rules.ArchitecturalRule

data class WorkspaceModuleSuggestions(
    val moduleId: String,
    val moduleRoot: String,
    val profile: ProjectProfileDetection,
    val rules: List<ArchitecturalRule>
)