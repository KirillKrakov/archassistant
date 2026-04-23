package com.example.archassistant.service.templates

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CleanArchitectureTemplateTest {

    private val context = TemplateContext(
        projectId = "test",
        basePackage = "com.example",
        architecturePattern = ArchitecturePattern.CLEAN_ARCHITECTURE,
        layers = mapOf(
            LayerType.DOMAIN to listOf(PackageInfo("com.example.domain", 5)),
            LayerType.INFRASTRUCTURE to listOf(PackageInfo("com.example.infrastructure", 3))
        ),
        annotations = mapOf("@Entity" to 4, "@Service" to 2),
        namingConventions = NamingConventions()
    )

    @Test
    fun `DomainIsolation is applicable when both domain and infrastructure exist`() {
        assertTrue(CleanArchitectureRules.DomainIsolation.isApplicable(context))
    }

    @Test
    fun `DomainIsolation generates correct rule`() {
        val rules = CleanArchitectureRules.DomainIsolation.generate(context)
        assertEquals(1, rules.size)
        val rule = rules.first()
        assertTrue(rule.id.startsWith("clean_domain_isolation_test"), "Rule id should start with expected prefix")
        assertEquals(RuleType.DEPENDENCY, rule.type)
        assertEquals(ConstraintType.NO_DEPENDENCY, rule.constraint)
        assertEquals(Severity.CRITICAL, rule.severity)
        assertTrue(rule.suggested)
    }

    @Test
    fun `ApplicationIsolation is applicable when application layer exists`() {
        val ctx = context.copy(
            layers = mapOf(
                LayerType.APPLICATION to listOf(PackageInfo("com.example.application", 4)),
                LayerType.INFRASTRUCTURE to listOf(PackageInfo("com.example.infrastructure", 3))
            )
        )
        assertTrue(CleanArchitectureRules.ApplicationIsolation.isApplicable(ctx))
    }

    @Test
    fun `template not applicable when required layer missing`() {
        val ctxWithoutInfra = context.copy(layers = mapOf(LayerType.DOMAIN to emptyList()))
        assertFalse(CleanArchitectureRules.DomainIsolation.isApplicable(ctxWithoutInfra))
        assertTrue(CleanArchitectureRules.DomainIsolation.generate(ctxWithoutInfra).isEmpty())
    }
}