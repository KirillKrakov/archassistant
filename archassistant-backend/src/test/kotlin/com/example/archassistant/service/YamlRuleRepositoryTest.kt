package com.example.archassistant.service

import com.example.archassistant.TestYamlConfig
import com.example.archassistant.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestYamlConfig::class)
class YamlRuleRepositoryTest {

    @Autowired
    private lateinit var repository: YamlRuleRepository

    @Autowired
    @Qualifier("yamlObjectMapper")  // FIXED: явный квалифайер
    private lateinit var yamlMapper: ObjectMapper

    private val testProjectId = "com.example.test"

    @Test
    fun `create and save default config`() {
        val config = repository.createDefault(testProjectId, "SPRING_BOOT")
        assertTrue(repository.save(config))
        assertTrue(repository.exists(testProjectId))
        val loaded = repository.load(testProjectId)
        assertNotNull(loaded)
        assertEquals(testProjectId, loaded!!.projectId)
    }

    @Test
    fun `load non-existent config returns null`() {
        assertNull(repository.load("non.existent.project"))
    }

    @Test
    fun `validate config with valid rules passes`() {
        val config = RulesConfig(
            projectId = testProjectId,
            rules = listOf(
                ArchitecturalRule(
                    id = "rule_001", name = "Test rule", type = RuleType.DEPENDENCY,
                    fromPackage = "..service..", toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY
                )
            )
        )
        assertTrue(repository.validate(config).passed)
    }

    @Test
    fun `validate config with empty projectId fails`() {
        val config = RulesConfig(projectId = "", rules = emptyList())
        val result = repository.validate(config)
        assertFalse(result.passed)
        assertTrue(result.violations.any { it.ruleId == "config_validation" })
    }

    @Test
    fun `validate config with invalid wildcard pattern warns`() {
        val config = RulesConfig(
            projectId = testProjectId,
            rules = listOf(
                ArchitecturalRule(
                    id = "rule_001", name = "Test", type = RuleType.DEPENDENCY,
                    fromPackage = "[invalid(regex", toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY
                )
            )
        )
        val result = repository.validate(config)
        assertTrue(result.violations.any { it.severity == Severity.WARNING })
    }

    @Test
    fun `getEnabledRules filters disabled rules`() {
        val config = RulesConfig(
            projectId = testProjectId,
            rules = listOf(
                ArchitecturalRule(
                    id = "rule_001", name = "Enabled", type = RuleType.DEPENDENCY,
                    fromPackage = "..service..", toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY, enabled = true
                ),
                ArchitecturalRule(
                    id = "rule_002", name = "Disabled", type = RuleType.DEPENDENCY,
                    fromPackage = "..repo..", toPackage = "..service..",
                    constraint = ConstraintType.NO_DEPENDENCY, enabled = false
                )
            )
        )
        val enabled = config.getEnabledRules()
        assertEquals(1, enabled.size)
        assertEquals("rule_001", enabled.first().id)
    }

    @Test
    fun `updateRule modifies correct rule`() {
        val config = RulesConfig(
            projectId = testProjectId,
            rules = listOf(
                ArchitecturalRule(
                    id = "rule_001", name = "Original", type = RuleType.DEPENDENCY,
                    fromPackage = "..service..", toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY, enabled = true
                )
            )
        )
        val updated = config.updateRule("rule_001") { it.copy(enabled = false, name = "Updated") }
        val updatedRule = updated.rules.first()
        assertEquals("Updated", updatedRule.name)
        assertFalse(updatedRule.enabled)
    }

    @Test
    fun `yaml serialization preserves all fields`() {
        val original = RulesConfig(
            projectId = testProjectId,
            projectType = ProjectType.KTOR,
            rules = listOf(
                ArchitecturalRule(
                    id = "rule_001", name = "Test", type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..", constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service", severity = Severity.INFO, weight = 0.5
                )
            ),
            settings = RuleSettings(maxIterations = 5, timeoutSeconds = 60)
        )
        val yaml = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original)
        val loaded = yamlMapper.readValue(yaml, RulesConfig::class.java)
        assertEquals(original.projectId, loaded.projectId)
        assertEquals(original.projectType, loaded.projectType)
        assertEquals(1, loaded.rules.size)
        assertEquals("rule_001", loaded.rules.first().id)
        assertEquals(5, loaded.settings.maxIterations)
    }

    @Test
    fun `delete config works`() {
        val config = repository.createDefault(testProjectId)
        repository.save(config)
        assertTrue(repository.exists(testProjectId))
        assertTrue(repository.delete(testProjectId))
        assertFalse(repository.exists(testProjectId))
    }
}