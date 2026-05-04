package com.example.archassistant.service

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

class RuleTemplateEngineTest {

    private lateinit var engine: RuleTemplateEngine

    private lateinit var layeredStructure: ProjectStructure
    private lateinit var cleanStructure: ProjectStructure

    @BeforeEach
    fun setUp() {
        engine = RuleTemplateEngine()
        // Структура для Layered Architecture (Spring Boot style)
        layeredStructure = ProjectStructure(
            projectId = "com.example.layered",
            architecturePattern = ArchitecturePattern.LAYERED,
            packages = listOf(
                "com.example.controller",
                "com.example.service",
                "com.example.repository",
                "com.example.entity"
            ),
            annotations = mapOf(
                "@RestController" to 5,
                "@Service" to 10,
                "@Repository" to 8,
                "@Entity" to 12
            ),
            namingConventions = NamingConventions(
                serviceSuffix = "Service",
                repositorySuffix = "Repository",
                controllerSuffix = "Controller"
            ),
            dependencies = listOf(
                Dependency("com.example.controller.UserController", "com.example.service.UserService", DependencyType.IMPORT),
                Dependency("com.example.service.UserService", "com.example.repository.UserRepository", DependencyType.IMPORT)
            )
        )

        // Структура для Clean Architecture
        cleanStructure = ProjectStructure(
            projectId = "com.example.clean",
            architecturePattern = ArchitecturePattern.CLEAN_ARCHITECTURE,
            packages = listOf(
                "com.example.domain.entity",
                "com.example.domain.repository",
                "com.example.application.service",
                "com.example.infrastructure.persistence"
            ),
            annotations = mapOf(
                "@Entity" to 15,
                "@Service" to 8,
                "@Repository" to 6
            ),
            namingConventions = NamingConventions(),
            dependencies = listOf(
                Dependency("com.example.application.service.UserService", "com.example.domain.repository.UserRepository", DependencyType.IMPORT)
            )
        )
    }

    @Test
    fun `suggest rules for layered architecture`() {
        val rules = engine.suggestRules(layeredStructure)

        assertTrue(rules.isNotEmpty(), "Should suggest at least one rule for layered architecture")

        // Проверяем, что предложено ключевое правило
        val serviceControllerRule = rules.find { it.id.contains("layered_service_controller") }
        assertNotNull(serviceControllerRule, "Should suggest service-controller dependency rule")
        assertEquals(Severity.CRITICAL, serviceControllerRule!!.severity)
        assertEquals(2.0, serviceControllerRule.weight)
    }

    @Test
    fun `suggest rules for clean architecture`() {
        val rules = engine.suggestRules(cleanStructure)

        assertTrue(rules.isNotEmpty(), "Should suggest at least one rule for clean architecture")

        // Проверяем правило изоляции domain слоя
        val domainIsolationRule = rules.find { it.id.contains("clean_domain_isolation") }
        assertNotNull(domainIsolationRule, "Should suggest domain isolation rule")
        assertEquals(Severity.CRITICAL, domainIsolationRule!!.severity)
    }

    @Test
    fun `naming convention rules are suggested when layer exists`() {
        val rules = engine.suggestRules(layeredStructure)

        val serviceNamingRule = rules.find { it.id.contains("layered_service_naming") }
        assertNotNull(serviceNamingRule, "Should suggest service naming rule when service layer exists")
        assertEquals("Service", serviceNamingRule!!.pattern)
    }

    @Test
    fun `rules are filtered by applicable patterns`() {
        // MVVM правила не должны предлагаться для Layered проекта
        val rules = engine.suggestRules(layeredStructure)

        val mvvmRules = rules.filter { it.id.contains("mvvm_") }
        assertTrue(mvvmRules.isEmpty(), "Should not suggest MVVM rules for Layered architecture")
    }

    @Test
    fun `rules are sorted by priority`() {
        val rules = engine.suggestRules(layeredStructure)

        // Проверяем, что правила с высоким приоритетом идут раньше
        val priorities = rules.map {
            // Извлекаем приоритет из шаблона (упрощённо)
            when {
                it.id.contains("service_controller") -> 100
                it.id.contains("naming") -> 40
                else -> 50
            }
        }

        // Приоритеты должны быть в порядке убывания (или хотя бы не возрастать)
        for (i in 1 until priorities.size) {
            assertTrue(priorities[i-1] >= priorities[i], "Rules should be sorted by priority descending")
        }
    }

    @Test
    fun `empty structure returns empty suggestions`() {
        val emptyStructure = ProjectStructure(
            projectId = "empty",
            packages = emptyList(),
            annotations = emptyMap()
        )

        val rules = engine.suggestRules(emptyStructure)
        assertTrue(rules.isEmpty(), "Should not suggest rules for empty project structure")
    }
}