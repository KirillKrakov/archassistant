package com.example.archassistant.service.templates

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LayeredArchitectureTemplateTest {

    private val context = TemplateContext(
        projectId = "test",
        basePackage = "com.example",
        architecturePattern = ArchitecturePattern.LAYERED,
        layers = mapOf(
            LayerType.SERVICE to listOf(PackageInfo("com.example.service", 10)),
            LayerType.CONTROLLER to listOf(PackageInfo("com.example.controller", 5))
        ),
        annotations = mapOf("@Service" to 10, "@RestController" to 5),
        namingConventions = NamingConventions(serviceSuffix = "Service")
    )

    @Test
    fun `ServiceControllerDependency is applicable when both layers exist`() {
        assertTrue(LayeredArchitectureRules.ServiceControllerDependency.isApplicable(context))
    }

    @Test
    fun `ServiceControllerDependency generates correct rule`() {
        val rules = LayeredArchitectureRules.ServiceControllerDependency.generate(context)

        assertEquals(1, rules.size)
        val rule = rules.first()

        assertTrue(rule.id.startsWith("layered_service_controller_test"), "Rule id should start with expected prefix")
        assertEquals("Services should not depend on controllers", rule.name)
        assertEquals(RuleType.DEPENDENCY, rule.type)
        assertEquals(ConstraintType.NO_DEPENDENCY, rule.constraint)
        assertEquals(Severity.CRITICAL, rule.severity)
        assertEquals(2.0, rule.weight)
        assertTrue(rule.suggested)
    }

    @Test
    fun `ServiceNaming generates rule with correct suffix`() {
        val rules = LayeredArchitectureRules.ServiceNaming.generate(context)

        assertEquals(1, rules.size)
        val rule = rules.first()

        assertEquals(RuleType.NAMING_CONVENTION, rule.type)
        assertEquals(ConstraintType.NAMING_SUFFIX, rule.constraint)
        assertEquals("Service", rule.pattern)
        assertEquals(Severity.INFO, rule.severity)
    }

    @Test
    fun `Template not applicable when layer missing`() {
        val contextWithoutController = context.copy(
            layers = mapOf(LayerType.SERVICE to listOf(PackageInfo("com.example.service", 10)))
        )

        assertFalse(LayeredArchitectureRules.ServiceControllerDependency.isApplicable(contextWithoutController))
        assertTrue(LayeredArchitectureRules.ServiceControllerDependency.generate(contextWithoutController).isEmpty())
    }
}