package com.example.archassistant.util

import com.example.archassistant.model.*
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DependencyAnalyzerTest {

    @TempDir
    lateinit var tempDir: java.nio.file.Path

    @Test
    fun `calculateDependencyCorrect returns 100 for no rules`() {
        val score = DependencyAnalyzer.calculateDependencyCorrect(tempDir, emptyList())
        assertEquals(100.0, score)
    }

    @Test
    fun `calculateDependencyCorrect detects forbidden dependency`() {
        val compiler = CodeCompiler()

        // Компилируем контроллер
        val controllerCode = "package com.example.controller; public class Ctrl { }"
        val ctrlRoot = compiler.compileCode(controllerCode, "Ctrl")
        val ctrlClassesDir = ctrlRoot.resolve("classes")

        // Компилируем сервис с зависимостью
        val serviceCode = """
            package com.example.service;
            import com.example.controller.Ctrl;
            public class Svc { private Ctrl c; }
        """.trimIndent()
        val svcRoot = compiler.compileCode(serviceCode, "Svc", ctrlClassesDir.toString())
        val svcClassesDir = svcRoot.resolve("classes")

        // Импортируем классы из обоих путей
        val importedClasses = ClassFileImporter()
            .importPaths(svcClassesDir, ctrlClassesDir)

        val rules = listOf(
            ArchitecturalRule(
                id = "no_ctrl_dep",
                name = "No controller dependency",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY
            )
        )

        val score = DependencyAnalyzer.calculateDependencyCorrect(importedClasses, rules)

        assertTrue(score < 100.0, "Should detect forbidden dependency")

        compiler.cleanup(ctrlRoot)
        compiler.cleanup(svcRoot)
    }

    @Test
    fun `analyzeWithViolations returns detailed violations`() {
        val compiler = CodeCompiler()

        val controllerCode = "package com.example.controller; public class UserController { }"
        val ctrlRoot = compiler.compileCode(controllerCode, "UserController")
        val ctrlClassesDir = ctrlRoot.resolve("classes")

        val serviceCode = """
            package com.example.service;
            import com.example.controller.UserController;
            public class UserService { private UserController ctrl; }
        """.trimIndent()
        val svcRoot = compiler.compileCode(serviceCode, "UserService", ctrlClassesDir.toString())
        val svcClassesDir = svcRoot.resolve("classes")

        val importedClasses = ClassFileImporter()
            .importPaths(svcClassesDir, ctrlClassesDir)

        val rules = listOf(
            ArchitecturalRule(
                id = "test_rule",
                name = "Test rule",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY,
                severity = Severity.CRITICAL
            )
        )

        val violations = DependencyAnalyzer.analyzeWithViolations(importedClasses, rules)

        assertTrue(violations.isNotEmpty())
        assertEquals("test_rule", violations.first().ruleId)
        assertEquals(Severity.CRITICAL, violations.first().severity)

        compiler.cleanup(ctrlRoot)
        compiler.cleanup(svcRoot)
    }
}