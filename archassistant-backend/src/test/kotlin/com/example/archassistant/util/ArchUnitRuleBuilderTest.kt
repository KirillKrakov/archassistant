package com.example.archassistant.util

import com.example.archassistant.model.*
import com.tngtech.archunit.lang.ArchRule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArchUnitRuleBuilderTest {

    @Test
    fun `build dependency rule with NO_DEPENDENCY constraint`() {
        val rule = ArchitecturalRule(
            id = "test",
            name = "Test",
            type = RuleType.DEPENDENCY,
            fromPackage = "..service..",
            toPackage = "..controller..",
            constraint = ConstraintType.NO_DEPENDENCY
        )

        val archRule = ArchUnitRuleBuilder.build(rule)

        assertNotNull(archRule)
        // ArchRule нельзя легко протестировать без компиляции, но проверяем, что не null
    }

    @Test
    fun `build naming rule with NAMING_SUFFIX constraint`() {
        val rule = ArchitecturalRule(
            id = "test",
            name = "Test",
            type = RuleType.NAMING_CONVENTION,
            fromPackage = "..service..",
            constraint = ConstraintType.NAMING_SUFFIX,
            pattern = "Service"
        )

        val archRule = ArchUnitRuleBuilder.build(rule)

        assertNotNull(archRule)
    }

    @Test
    fun `build annotation rule with HAS_ANNOTATION constraint`() {
        val rule = ArchitecturalRule(
            id = "test",
            name = "Test",
            type = RuleType.ANNOTATION_CHECK,
            fromPackage = "..service..",
            constraint = ConstraintType.HAS_ANNOTATION,
            annotation = "org.springframework.stereotype.Service"
        )

        val archRule = ArchUnitRuleBuilder.build(rule)

        assertNotNull(archRule)
    }

    @Test
    fun `unsupported constraint throws exception`() {
        val rule = ArchitecturalRule(
            id = "test",
            name = "Test",
            type = RuleType.DEPENDENCY,
            fromPackage = "..service..",
            constraint = ConstraintType.NAMING_SUFFIX, // Не поддерживается для DEPENDENCY
            pattern = "Service"
        )

        assertThrows<UnsupportedOperationException> {
            ArchUnitRuleBuilder.build(rule)
        }
    }

    @Test
    fun `custom rule type returns null`() {
        val rule = ArchitecturalRule(
            id = "test",
            name = "Test",
            type = RuleType.CUSTOM,
            fromPackage = "..*",
            constraint = ConstraintType.CUSTOM
        )

        val archRule = ArchUnitRuleBuilder.build(rule)

        assertNull(archRule)
    }

    @Test
    fun `build dependency rule with toPackages list`() {
        val rule = ArchitecturalRule(
            id = "multi_target",
            name = "Multi target test",
            type = RuleType.DEPENDENCY,
            fromPackage = "..service..",
            toPackages = listOf("..controller..", "..ui.."),
            constraint = ConstraintType.NO_DEPENDENCY
        )
        val archRule = ArchUnitRuleBuilder.build(rule)
        assertNotNull(archRule)
        // Проверка, что правило включает оба целевых пакета (через toString, не идеально, но приемлемо)
        val ruleDesc = archRule?.toString() ?: ""
        assertTrue(ruleDesc.contains("controller") && ruleDesc.contains("ui"))
    }
}