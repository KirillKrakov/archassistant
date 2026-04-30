package com.example.archassistant.service.rules

import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.ProjectProfile

interface ProfileRuleStrategy {
    val profile: ProjectProfile
    fun generate(context: RuleGenerationContext): List<ArchitecturalRule>
}