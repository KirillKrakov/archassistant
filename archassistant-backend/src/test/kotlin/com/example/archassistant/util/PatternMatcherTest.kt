package com.example.archassistant.util

import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PatternMatcherTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `calculatePatternMatch returns 100 for empty rules`() {
        val score = PatternMatcher.calculatePatternMatch(tempDir, emptyList())
        assertEquals(100.0, score)
    }

    @Test
    fun `calculatePatternMatch detects suffix violation`() {
        // Создаём тестовый код с нарушением
        val code = """
            package com.example.service;
            public class WrongName { }
        """.trimIndent()

        val compiler = CodeCompiler()
        val root = compiler.compileCode(code, "WrongName")

        val rules = listOf(
            ArchitecturalRule(
                id = "test",
                name = "Test",
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..",
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service"
            )
        )

        val score = PatternMatcher.calculatePatternMatch(root.resolve("classes"), rules)

        assertTrue(score < 100.0, "Should detect naming violation")

        compiler.cleanup(root)
    }

    @Test
    fun `checkWithViolations returns specific violations`() {
        val code = """
            package com.example.service;
            class BadService { }
            class GoodService { }
        """.trimIndent()

        val compiler = CodeCompiler()
        val root = compiler.compileCode(code, "MultiClass")

        val rules = listOf(
            ArchitecturalRule(
                id = "prefix_test",
                name = "Prefix test",
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..",
                constraint = ConstraintType.NAMING_PREFIX,
                pattern = "Good"
            )
        )

        val violations = PatternMatcher.checkWithViolations(root.resolve("classes"), rules)

        assertTrue(violations.any { it.className.contains("BadService") })
        assertFalse(violations.any { it.className.contains("GoodService") })

        compiler.cleanup(root)
    }
}