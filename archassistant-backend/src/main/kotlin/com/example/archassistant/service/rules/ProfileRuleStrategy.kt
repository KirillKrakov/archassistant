package com.example.archassistant.service.rules

import com.example.archassistant.model.context.ProjectProfile
import com.example.archassistant.model.rules.ArchitecturalRule

interface ProfileRuleStrategy {
    val profile: ProjectProfile
    fun generate(context: RuleGenerationContext): List<ArchitecturalRule>
}