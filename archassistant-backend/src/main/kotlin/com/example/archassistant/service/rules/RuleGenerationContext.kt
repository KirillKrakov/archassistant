package com.example.archassistant.service.rules

import com.example.archassistant.model.PackageScopeIndex
import com.example.archassistant.model.ProjectProfileDetection
import com.example.archassistant.model.ProjectStructure

data class RuleGenerationContext(
    val structure: ProjectStructure,
    val index: PackageScopeIndex,
    val detection: ProjectProfileDetection
) {
    val basePackage: String get() = index.basePackage
    val projectId: String get() = structure.projectId
}