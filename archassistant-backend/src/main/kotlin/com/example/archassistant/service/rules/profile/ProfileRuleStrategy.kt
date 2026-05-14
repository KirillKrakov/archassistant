package com.example.archassistant.service.rules.profile

import com.example.archassistant.model.context.ProjectProfile
import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.service.rules.RuleGenerationContext

interface ProfileRuleStrategy {
    val profile: ProjectProfile
    fun generate(context: RuleGenerationContext): List<ArchitecturalRule>
}