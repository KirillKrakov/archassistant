package com.example.archassistant.service

import com.example.archassistant.model.Severity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArchUnitValidatorTest {
    private lateinit var validator: ArchUnitValidator

    @BeforeEach
    fun setUp() {
        validator = ArchUnitValidator()
    }

    @Test
    fun `valid Java code passes basic validation`() {
        val code = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            @Service
            public class UserService {
                public void createUser(String name) { }
            }
        """.trimIndent()
        val result = validator.validateBasic(code, "UserService")
        assertTrue(result.passed)
        assertTrue(result.violations.isEmpty())
        assertTrue(result.executionTimeMs > 0)
    }

    @Test
    fun `valid Kotlin code passes basic validation`() {
        val code = """
            package com.example.service
            import org.springframework.stereotype.Service
            @Service
            class UserService {
                fun createUser(name: String) { }
            }
        """.trimIndent()
        val result = validator.validateBasic(code, "UserService")
        assertTrue(result.passed)
        assertTrue(result.violations.isEmpty())
        assertTrue(result.executionTimeMs > 0)
    }

    @Test
    fun `invalid code fails basic validation`() {
        val code = "public void invalid() { }"
        val result = validator.validateBasic(code, "InvalidClass")
        assertFalse(result.passed)
        assertEquals(Severity.CRITICAL, result.violations.first().severity)
        assertTrue(result.executionTimeMs > 0)
    }

    @Test
    fun `naming convention rule works for Java`() {
        val validCode = """
        package com.example.service;
        import org.springframework.stereotype.Service;
        @Service
        public class UserService { }
    """.trimIndent()
        val invalidCode = """
        package com.example.service;
        import org.springframework.stereotype.Service;
        @Service
        public class UserLogic { }
    """.trimIndent()
        val rule = validator.namingConventionRule("..service", "Service")

        val validResult = validator.validate(validCode, "UserService", listOf(rule))
        assertTrue(validResult.passed, "Valid code should pass naming rule")

        val invalidResult = validator.validate(invalidCode, "UserLogic", listOf(rule))
        assertFalse(invalidResult.passed, "Invalid code should fail naming rule")
        assertTrue(invalidResult.violations.any { it.ruleId == "rule_1" })
    }

    @Test
    fun `naming convention rule works for Kotlin`() {
        val validCode = """
        package com.example.service
        import org.springframework.stereotype.Service
        @Service
        class UserService
    """.trimIndent()
        val invalidCode = """
        package com.example.service
        import org.springframework.stereotype.Service
        @Service
        class UserLogic
    """.trimIndent()
        // Исправлено: точный пакет
        val rule = validator.namingConventionRule("..service", "Service")

        val validResult = validator.validate(validCode, "UserService", listOf(rule))
        assertTrue(validResult.passed, "Valid Kotlin code should pass naming rule")

        val invalidResult = validator.validate(invalidCode, "UserLogic", listOf(rule))
        assertFalse(invalidResult.passed, "Invalid Kotlin code should fail naming rule")
        assertTrue(invalidResult.violations.any { it.ruleId == "rule_1" })
    }

    @Test
    fun `no dependency rule detects violation`() {
        val code = """
            package com.example.service;
            import com.example.controller.UserController;
            @Service
            public class UserService {
                private UserController controller;
            }
        """.trimIndent()
        val rule = validator.noDependencyRule("..service..", "..controller..")
        val result = validator.validate(code, "UserService", listOf(rule))
        assertFalse(result.passed)
        assertTrue(result.violations.isNotEmpty())
        assertTrue(result.executionTimeMs > 0)
    }

    @Test
    fun `multiple rules can be applied`() {
        val code = """
        package com.example.service;
        import org.springframework.stereotype.Service;
        @Service
        public class OrderService { }
    """.trimIndent()
        val rules = listOf(
            validator.namingConventionRule("..service", "Service"),
            validator.noDependencyRule("..service..", "..controller..")
        )
        val result = validator.validate(code, "OrderService", rules)
        assertTrue(result.passed, "Violations: ${result.violations}")
    }
}