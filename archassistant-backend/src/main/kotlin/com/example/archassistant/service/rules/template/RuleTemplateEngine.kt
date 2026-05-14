package com.example.archassistant.service.rules.template

import com.example.archassistant.model.*
import com.example.archassistant.service.context.detection.ArchitectureDetector
import com.example.archassistant.service.rules.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RuleTemplateEngine(
    private val architectureDetector: ArchitectureDetector,
    private val strategies: List<ProfileRuleStrategy>
) {

    private val logger = LoggerFactory.getLogger(RuleTemplateEngine::class.java)

    fun suggestRules(structure: ProjectStructure): List<ArchitecturalRule> {
        logger.debug("Generating rule suggestions for project: ${structure.projectId}")

        val index = PackageScopeIndex.from(structure)
        val detection = structure.detection ?: architectureDetector.detect(structure)

        logger.debug(
            "Detected project profile: {}, confidence: {}",
            detection.primaryProfile,
            detection.confidence
        )

        val context = RuleGenerationContext(
            structure = structure,
            index = index,
            detection = detection
        )

        if (detection.primaryProfile == ProjectProfile.UNKNOWN) {
            logger.debug("Unknown profile detected, no rules generated")
            return emptyList()
        }

        val generated = strategies
            .filter { it.profile == detection.primaryProfile }
            .flatMap { strategy ->
                try {
                    strategy.generate(context)
                } catch (e: Exception) {
                    logger.warn("Failed to generate rules for profile ${strategy.profile}: ${e.message}")
                    emptyList()
                }
            }

        val result = generated
            .distinctBy { semanticKey(it) }
            .sortedWith(
                compareByDescending<ArchitecturalRule> { severityRank(it.severity) }
                    .thenByDescending { specificityScore(it) }
                    .thenByDescending { it.weight }
                    .thenBy { it.id }
            )

        logger.debug("Generated ${result.size} rule suggestions")
        return result
    }

    private fun semanticKey(rule: ArchitecturalRule): String {
        return listOf(
            rule.type.name,
            rule.constraint.name,
            rule.fromSelectorMode.name,
            rule.toSelectorMode.name,
            rule.fromPackage,
            rule.toPackage.orEmpty(),
            rule.toPackages?.joinToString(",").orEmpty(),
            rule.pattern.orEmpty(),
            rule.annotation.orEmpty(),
            rule.fromClassType?.name.orEmpty(),
            rule.toClassType?.name.orEmpty()
        ).joinToString("|")
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 4
        Severity.ERROR -> 3
        Severity.WARNING -> 2
        Severity.INFO -> 1
    }

    private fun specificityScore(rule: ArchitecturalRule): Int {
        var score = 0
        if (rule.fromClassType != null) score += 4
        if (rule.toClassType != null) score += 4
        if (rule.annotation != null) score += 2
        if (rule.pattern != null) score += 2
        if (rule.fromPackage.count { it == '.' } > 2) score += 1
        if ((rule.toPackage?.count { it == '.' } ?: 0) > 2) score += 1
        return score
    }
}