package com.example.archassistant.service

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DynamicRuleValidatorTest {

    private lateinit var validator: DynamicRuleValidator

    @BeforeEach
    fun setUp() {
        validator = DynamicRuleValidator()
    }

    @Test
    fun `valid service code passes basic validation`() {
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
    fun `invalid code fails basic validation`() {
        val code = "public void invalid() { }" // Missing class declaration

        val result = validator.validateBasic(code, "InvalidClass")

        assertFalse(result.passed)
        assertEquals(Severity.CRITICAL, result.violations.first().severity)
    }

    @Test
    fun `dependency rule detects violation`() {
        // Определяем оба класса в одном фрагменте кода
        val code = """
            package com.example;
            class UserController {}
            
            public class UserService {
                private UserController controller;
            }
        """.trimIndent()
        val rule = ArchitecturalRule(
            id = "test_rule",
            name = "Services should not depend on controllers",
            type = RuleType.DEPENDENCY,
            fromPackage = "..service..",
            toPackage = "..controller..",
            constraint = ConstraintType.NO_DEPENDENCY,
            severity = Severity.CRITICAL
        )
        val result = validator.validate(code, "UserService", listOf(rule))
        assertFalse(result.passed)
        assertTrue(result.violations.any { it.ruleId == "test_rule" })
    }

    @Test
    fun `naming convention rule works`() {
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

        val rule = ArchitecturalRule(
            id = "naming_rule",
            name = "Services should have Service suffix",
            type = RuleType.NAMING_CONVENTION,
            fromPackage = "..service..",
            constraint = ConstraintType.NAMING_SUFFIX,
            pattern = "Service",
            severity = Severity.INFO
        )

        // Валидный код должен пройти
        val validResult = validator.validate(validCode, "UserService", listOf(rule))
        assertTrue(validResult.passed)

        // Неверный код должен провалиться
        val invalidResult = validator.validate(invalidCode, "UserLogic", listOf(rule))
        assertFalse(invalidResult.passed)
        assertTrue(invalidResult.violations.any { it.ruleId == "naming_rule" })
    }

    @Test
    fun `annotation rule works`() {
        val validCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class AnnotatedService { }
        """.trimIndent()

        val invalidCode = """
            package com.example.service;
            
            public class UnannotatedService { }
        """.trimIndent()

        val rule = ArchitecturalRule(
            id = "annotation_rule",
            name = "Services must have @Service annotation",
            type = RuleType.ANNOTATION_CHECK,
            fromPackage = "..service..",
            constraint = ConstraintType.HAS_ANNOTATION,
            annotation = "org.springframework.stereotype.Service",
            severity = Severity.WARNING
        )

        val validResult = validator.validate(validCode, "AnnotatedService", listOf(rule))
        assertTrue(validResult.passed)

        val invalidResult = validator.validate(invalidCode, "UnannotatedService", listOf(rule))
        assertFalse(invalidResult.passed)
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
            ArchitecturalRule(
                id = "naming",
                name = "Naming",
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..",
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service"
            ),
            ArchitecturalRule(
                id = "no_controller_dep",
                name = "No controller dependency",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY
            )
        )

        val result = validator.validate(code, "OrderService", rules)

        assertTrue(result.passed)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `disabled rules are ignored`() {
        // Определяем оба класса в одном фрагменте кода — компиляция пройдёт
        val code = """
            package com.example;
            class UserController {}
            
            public class UserService {
                private UserController controller;
            }
        """.trimIndent()
        val disabledRule = ArchitecturalRule(
            id = "disabled",
            name = "Disabled rule",
            type = RuleType.DEPENDENCY,
            fromPackage = "..service..",
            toPackage = "..controller..",
            constraint = ConstraintType.NO_DEPENDENCY,
            enabled = false
        )
        // Даже при наличии зависимости, правило отключено, валидация должна пройти
        val result = validator.validate(code, "UserService", listOf(disabledRule))
        assertTrue(result.passed)
    }

    @Test
    fun `execution time is recorded`() {
        val code = """
            package com.example.test;
            public class TimingTest { }
        """.trimIndent()

        val result = validator.validateBasic(code, "TimingTest")

        assertTrue(result.executionTimeMs >= 0)
        assertTrue(result.executionTimeMs < 10000) // < 10 секунд для простого кода
    }
}