package com.example.archassistant.service.templates

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HexagonalTemplateTest {

    private val context = TemplateContext(
        projectId = "test",
        basePackage = "com.example",
        architecturePattern = ArchitecturePattern.HEXAGONAL,
        layers = mapOf(
            LayerType.DOMAIN to listOf(PackageInfo("com.example.domain", 5)),
            LayerType.ADAPTER to listOf(PackageInfo("com.example.adapter", 3)),
            LayerType.APPLICATION to listOf(PackageInfo("com.example.application", 4)),
            LayerType.PORT to listOf(PackageInfo("com.example.port", 2))
        ),
        annotations = mapOf("@Service" to 2),
        namingConventions = NamingConventions()
    )

    @Test
    fun `DomainPortsIsolation is applicable when domain and adapter exist`() {
        assertTrue(HexagonalRules.DomainPortsIsolation.isApplicable(context))
    }

    @Test
    fun `DomainPortsIsolation generates correct rule`() {
        val rules = HexagonalRules.DomainPortsIsolation.generate(context)
        assertEquals(1, rules.size)
        val rule = rules.first()
        assertTrue(rule.id.startsWith("hex_domain_ports_test"), "Rule id should start with expected prefix")
        assertEquals(RuleType.DEPENDENCY, rule.type)
        assertEquals(ConstraintType.NO_DEPENDENCY, rule.constraint)
        assertEquals(Severity.CRITICAL, rule.severity)
        assertTrue(rule.suggested)
    }

    @Test
    fun `PortsAbstraction is applicable when application and port exist`() {
        assertTrue(HexagonalRules.PortsAbstraction.isApplicable(context))
    }

    @Test
    fun `PortsAbstraction generates correct rule`() {
        val rules = HexagonalRules.PortsAbstraction.generate(context)
        assertEquals(1, rules.size)
        val rule = rules.first()
        assertTrue(rule.id.startsWith("hex_ports_abstraction_test"), "Rule id should start with expected prefix")
        assertEquals(RuleType.DEPENDENCY, rule.type)
        assertEquals(ConstraintType.MUST_DEPEND, rule.constraint)
        assertEquals(Severity.WARNING, rule.severity)
        assertTrue(rule.suggested)
    }

    @Test
    fun `template not applicable when required layer missing`() {
        val ctxWithoutDomain = context.copy(layers = mapOf(LayerType.ADAPTER to emptyList()))
        assertFalse(HexagonalRules.DomainPortsIsolation.isApplicable(ctxWithoutDomain))
        assertTrue(HexagonalRules.DomainPortsIsolation.generate(ctxWithoutDomain).isEmpty())
    }
}