package com.example.archassistant.model

import com.example.archassistant.dto.GenerationResponseFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DomainModelsTest {

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun `ArchitecturalRule serializes and deserializes correctly`() {
        val rule = ArchitecturalRule(
            id = "rule_001",
            name = "Services should not depend on controllers",
            description = "Сервисы не должны зависеть от контроллеров",
            type = RuleType.DEPENDENCY,
            fromPackage = "..service..",
            toPackage = "..controller..",
            constraint = ConstraintType.NO_DEPENDENCY,
            severity = Severity.CRITICAL,
            weight = 2.0,
            enabled = true,
            suggested = true
        )

        val json = objectMapper.writeValueAsString(rule)
        val deserialized = objectMapper.readValue(json, ArchitecturalRule::class.java)

        assertEquals(rule.id, deserialized.id)
        assertEquals(rule.type, deserialized.type)
        assertEquals(rule.constraint, deserialized.constraint)
        assertEquals(rule.weight, deserialized.weight)
    }

    @Test
    fun `ComplianceScore calculates correctly with default weights`() {
        val score = ComplianceScore.calculate(
            rulesPass = 80.0,
            patternMatch = 70.0,
            dependencyCorrect = 83.0
        )

        // (1.0×80 + 0.5×70 + 0.5×83) / 2.0 = 78.25
        assertEquals(78.25, score.total, 0.01)
        assertEquals(80.0, score.rulesPass)
        assertEquals(70.0, score.patternMatch)
        assertEquals(83.0, score.dependencyCorrect)
    }

    @Test
    fun `ComplianceScore respects custom weights`() {
        val weights = ScoreWeights(
            rulesPass = 2.0,
            patternMatch = 1.0,
            dependencyCorrect = 1.0
        )
        val score = ComplianceScore.calculate(
            rulesPass = 80.0,
            patternMatch = 70.0,
            dependencyCorrect = 83.0,
            weights = weights
        )

        // (2.0×80 + 1.0×70 + 1.0×83) / 4.0 = 78.25
        assertEquals(78.25, score.total, 0.01)
    }

    @Test
    fun `ComplianceScore grade classification works`() {
        assertEquals(ScoreGrade.EXCELLENT, ComplianceScore(95.0, 95.0, 95.0, 95.0).getGrade())
        assertEquals(ScoreGrade.GOOD, ComplianceScore(85.0, 85.0, 85.0, 85.0).getGrade())
        assertEquals(ScoreGrade.ACCEPTABLE, ComplianceScore(75.0, 75.0, 75.0, 75.0).getGrade())
        assertEquals(ScoreGrade.NEEDS_IMPROVEMENT, ComplianceScore(60.0, 60.0, 60.0, 60.0).getGrade())
        assertEquals(ScoreGrade.FAIL, ComplianceScore(40.0, 40.0, 40.0, 40.0).getGrade())
    }

// model/DomainModelsTest.kt

    @Test
    fun `ProjectStructure determines class type correctly without annotations`() {
        // Создаём структуру без аннотаций — определяем только по имени/пакету
        val structure = ProjectStructure(projectId = "test")

        // По пакету
        assertEquals(ClassType.SERVICE, structure.determineClassType("UserService", "com.example.service"))
        assertEquals(ClassType.CONTROLLER, structure.determineClassType("UserController", "com.example.controller"))
        assertEquals(ClassType.REPOSITORY, structure.determineClassType("UserRepository", "com.example.repository"))
        assertEquals(ClassType.ENTITY, structure.determineClassType("User", "com.example.entity"))
        assertEquals(ClassType.DTO, structure.determineClassType("UserDto", "com.example.dto"))

        // По имени класса (суффикс)
        assertEquals(ClassType.SERVICE, structure.determineClassType("OrderService", "com.example.business"))
        assertEquals(ClassType.CONTROLLER, structure.determineClassType("ProductController", "com.example.web"))
        assertEquals(ClassType.REPOSITORY, structure.determineClassType("OrderRepository", "com.example.data"))

        // Другое
        assertEquals(ClassType.OTHER, structure.determineClassType("Utils", "com.example.util"))
        assertEquals(ClassType.OTHER, structure.determineClassType("Config", "com.example.config"))
        assertEquals(ClassType.OTHER, structure.determineClassType("Main", "com.example"))
    }

    @Test
    fun `StrategyType serialization works`() {
        val json = objectMapper.writeValueAsString(StrategyType.HYBRID)
        assertEquals("\"HYBRID\"", json)

        val deserialized = objectMapper.readValue("\"PRE\"", StrategyType::class.java)
        assertEquals(StrategyType.PRE, deserialized)
    }

    @Test
    fun `GenerationResponseFactory creates success response`() {
        val response = GenerationResponseFactory.success(
            code = "public class Test {}",
            score = ComplianceScore(85.0, 80.0, 90.0, 85.0),
            strategy = StrategyType.HYBRID,
            iterations = 2,
            generationTimeMs = 1500,
            validationTimeMs = 300,
            model = "gpt-4"
        )

        assertTrue(response.success)
        assertNotNull(response.data)
        assertEquals("public class Test {}", response.data?.code)
        assertEquals(1800, response.metadata.totalTimeMs)
    }

    @Test
    fun `GenerationResponseFactory creates error response`() {
        val response = GenerationResponseFactory.error(
            errorCode = "COMPILATION_ERROR",
            message = "Failed to compile generated code",
            totalTimeMs = 500
        )

        assertFalse(response.success)
        assertNotNull(response.error)
        assertEquals("COMPILATION_ERROR", response.error?.code)
    }

    @Test
    fun `ArchitecturalRule appliesToPackage works correctly`() {
        val rule = ArchitecturalRule(
            id = "test",
            name = "Test rule",
            type = RuleType.DEPENDENCY,
            fromPackage = "com.example.service..*",
            toPackage = "com.example.controller..*",
            constraint = ConstraintType.NO_DEPENDENCY
        )

        // Должно матчить: классы в service пакете или его подпакетах
        assertTrue(rule.appliesToPackage("com.example.service.UserService"))
        assertTrue(rule.appliesToPackage("com.example.service.subpackage.SomeService"))

        // Не должно матчить: другие пакеты
        assertFalse(rule.appliesToPackage("com.example.controller.UserController"))
        assertFalse(rule.appliesToPackage("com.example.dto.UserDto"))
    }
}