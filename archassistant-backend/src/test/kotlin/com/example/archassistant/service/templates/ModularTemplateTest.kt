package com.example.archassistant.service.templates

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ModularTemplateTest {

    private val context = TemplateContext(
        projectId = "test",
        basePackage = "com.example",
        architecturePattern = ArchitecturePattern.MODULAR,
        layers = mapOf(
            LayerType.API to listOf(PackageInfo("com.example.api", 3)),
            LayerType.IMPL to listOf(PackageInfo("com.example.impl", 5)),
            LayerType.OTHER to listOf(
                PackageInfo("com.example.common", 2),
                PackageInfo("com.example.feature", 4)
            )
        ),
        annotations = mapOf("@Module" to 2),
        namingConventions = NamingConventions()
    )

    @Test
    fun `ModuleApiIsolation is applicable when both impl and api exist`() {
        assertTrue(ModularRules.ModuleApiIsolation.isApplicable(context))
    }

    @Test
    fun `ModuleApiIsolation generates correct rule`() {
        val rules = ModularRules.ModuleApiIsolation.generate(context)
        assertEquals(1, rules.size)
        val rule = rules.first()
        assertTrue(rule.id.startsWith("modular_api_isolation_test"), "Rule id should start with expected prefix")
        assertEquals(RuleType.DEPENDENCY, rule.type)
        assertEquals(ConstraintType.NO_DEPENDENCY, rule.constraint)
        assertEquals(Severity.CRITICAL, rule.severity)
        assertTrue(rule.suggested)
    }

    @Test
    fun `CommonModuleIsolation is applicable when both common and feature layers exist`() {
        // Для этого теста создадим контекст, где есть два пакета OTHER, помеченные как common и feature
        // В текущей реализации CommonModuleIsolation использует fromLayer = OTHER, toLayer = OTHER.
        // Чтобы проверить применимость, нужно, чтобы в контексте было хотя бы два различных пакета типа OTHER.
        assertTrue(ModularRules.CommonModuleIsolation.isApplicable(context))
    }

    @Test
    fun `CommonModuleIsolation generates rule`() {
        val rules = ModularRules.CommonModuleIsolation.generate(context)
        // Генерация может создать несколько правил, если пакетов несколько.
        assertTrue(rules.isNotEmpty())
        val rule = rules.first()
        assertTrue(rule.id.startsWith("modular_common_isolation_test"), "Rule id should start with expected prefix")
        assertEquals(RuleType.DEPENDENCY, rule.type)
        assertEquals(ConstraintType.NO_DEPENDENCY, rule.constraint)
        assertEquals(Severity.ERROR, rule.severity)
    }

    @Test
    fun `template not applicable when required layers missing`() {
        val ctxWithoutApi = context.copy(layers = mapOf(LayerType.IMPL to emptyList()))
        assertFalse(ModularRules.ModuleApiIsolation.isApplicable(ctxWithoutApi))
        assertTrue(ModularRules.ModuleApiIsolation.generate(ctxWithoutApi).isEmpty())
    }
}