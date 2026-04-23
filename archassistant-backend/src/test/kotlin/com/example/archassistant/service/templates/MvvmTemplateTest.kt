package com.example.archassistant.service.templates

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MvvmTemplateTest {

    private val context = TemplateContext(
        projectId = "test",
        basePackage = "com.example",
        architecturePattern = ArchitecturePattern.MVVM,
        layers = mapOf(
            LayerType.VIEWMODEL to listOf(PackageInfo("com.example.viewmodel", 4)),
            LayerType.VIEW to listOf(PackageInfo("com.example.view", 3))
        ),
        annotations = mapOf("@ViewModel" to 2),
        namingConventions = NamingConventions()
    )

    @Test
    fun `ViewModelViewDependency is applicable when both layers exist`() {
        assertTrue(MvvmArchitectureRules.ViewModelViewDependency.isApplicable(context))
    }

    @Test
    fun `ViewModelViewDependency generates correct rule`() {
        val rules = MvvmArchitectureRules.ViewModelViewDependency.generate(context)
        assertEquals(1, rules.size)
        val rule = rules.first()
        assertTrue(rule.id.startsWith("mvvm_viewmodel_view_test"), "Rule id should start with expected prefix")
        assertEquals(RuleType.DEPENDENCY, rule.type)
        assertEquals(ConstraintType.NO_DEPENDENCY, rule.constraint)
        assertEquals(Severity.CRITICAL, rule.severity)
    }

    @Test
    fun `ModelNaming is applicable when entity layer exists`() {
        val ctx = context.copy(
            layers = mapOf(LayerType.ENTITY to listOf(PackageInfo("com.example.model", 2)))
        )
        assertTrue(MvvmArchitectureRules.ModelNaming.isApplicable(ctx))
    }

    @Test
    fun `template not applicable when layer missing`() {
        val ctxWithoutView = context.copy(layers = mapOf(LayerType.VIEWMODEL to emptyList()))
        assertFalse(MvvmArchitectureRules.ViewModelViewDependency.isApplicable(ctxWithoutView))
        assertTrue(MvvmArchitectureRules.ViewModelViewDependency.generate(ctxWithoutView).isEmpty())
    }
}