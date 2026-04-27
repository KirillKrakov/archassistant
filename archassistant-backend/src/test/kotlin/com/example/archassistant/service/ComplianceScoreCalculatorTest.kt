package com.example.archassistant.service

import com.example.archassistant.model.*
import com.example.archassistant.util.CodeCompiler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ComplianceScoreCalculatorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var calculator: ComplianceScoreCalculator
    private lateinit var compiler: CodeCompiler

    private fun buildClasspath(vararg extraPaths: String): String {
        val cp = System.getProperty("java.class.path")
        val paths = cp.split(File.pathSeparator).toMutableList()
        paths.addAll(extraPaths)
        return paths.joinToString(File.pathSeparator)
    }

    @BeforeEach
    fun setUp() {
        compiler = CodeCompiler()
        calculator = ComplianceScoreCalculator(compiler)
    }

    @Test
    fun `calculate returns perfect score for valid code with no rules`() {
        val code = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                public void createUser(String name) { }
            }
        """.trimIndent()

        val score = calculator.calculate(code, "UserService", emptyList())

        assertEquals(100.0, score.total)
        assertEquals(100.0, score.rulesPass)
        assertEquals(100.0, score.patternMatch)
        assertEquals(100.0, score.dependencyCorrect)
        assertEquals(ScoreGrade.EXCELLENT, score.getGrade())
    }

    @Test
    fun `calculate detects naming convention violation`() {
        val code = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserLogic { }
        """.trimIndent()
        val rules = listOf(
            ArchitecturalRule(
                id = "service_suffix",
                name = "Service suffix",
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..",
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service",
                severity = Severity.INFO,
                weight = 0.5
            )
        )
        val fullClasspath = buildClasspath()
        val score = calculator.calculate(code, "UserLogic", rules, classpath = fullClasspath)

        assertTrue(score.patternMatch < 100.0, "PatternMatch should detect naming violation")
        assertTrue(score.violations.any { it.ruleId == "service_suffix" })
    }

    @Test
    fun `calculate detects dependency violation`() {
        // Компилируем контроллер
        val controllerCode = """
            package com.example.controller;
            public class UserController { }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val controllerClassesDir = controllerRoot.resolve("classes").toString()

        // Код сервиса с зависимостью
        val serviceCode = """
            package com.example.service;
            import com.example.controller.UserController;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                private UserController controller;
            }
        """.trimIndent()

        val rules = listOf(
            ArchitecturalRule(
                id = "no_controller_dep",
                name = "No controller dependency",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY,
                severity = Severity.CRITICAL,
                weight = 1.0
            )
        )

        val fullClasspath = buildClasspath(controllerClassesDir)
        val score = calculator.calculate(serviceCode, "UserService", rules, classpath = fullClasspath)
        assertTrue(score.dependencyCorrect < 100.0, "DependencyCorrect should detect violation")
        assertTrue(score.violations.any { it.ruleId == "no_controller_dep" })
        assertEquals(Severity.CRITICAL, score.violations.first().severity)

        compiler.cleanup(controllerRoot)
    }

    @Test
    fun `calculate respects custom weights`() {
        val code = """
            package com.example.service;
            public class TestService { }
        """.trimIndent()

        val rules = listOf(
            ArchitecturalRule(
                id = "naming",
                name = "Naming",
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..",
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service",
                weight = 2.0  // Высокий вес
            )
        )

        // Веса: rulesPass=1.0, patternMatch=2.0, dependencyCorrect=0.5
        val weights = ScoreWeights(rulesPass = 1.0, patternMatch = 2.0, dependencyCorrect = 0.5)
        val score = calculator.calculate(code, "TestService", rules, weights)

        // PatternMatch имеет больший вес, поэтому влияет на total сильнее
        // (конкретное значение зависит от реализации, но формула должна соблюдаться)
        val expectedTotal = (1.0 * score.rulesPass + 2.0 * score.patternMatch + 0.5 * score.dependencyCorrect) / 3.5
        assertEquals(expectedTotal, score.total, 0.01)
    }

    @Test
    fun `isPassing returns true for code above threshold`() {
        val code = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            @Service
            public class GoodService { }
        """.trimIndent()
        val fullClasspath = buildClasspath()  // использует classpath текущего процесса (уже есть Spring)
        val passing = calculator.isPassing(code, "GoodService", emptyList(), threshold = 70.0, classpath = fullClasspath)
        assertTrue(passing, "Valid code with no rules should pass threshold")
    }

    @Test
    fun `isPassing returns false for code below threshold`() {
        val code = """
            package com.example.service;
            public class BadName { }
        """.trimIndent()
        val rules = listOf(
            ArchitecturalRule(
                id = "strict_naming",
                name = "Strict naming",
                type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..",
                constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service",
                weight = 10.0
            )
        )
        val passing = calculator.isPassing(code, "BadName", rules, threshold = 90.0)
        assertFalse(passing, "Code with naming violation should not pass high threshold")
    }

    @Test
    fun `calculateForCompiled works without recompilation`() {
        // Компилируем код один раз
        val code = """
            package com.example.test;
            public class CompiledTest { }
        """.trimIndent()
        val root = compiler.compileCode(code, "CompiledTest")
        val classesDir = root.resolve("classes")

        // Считаем скор для скомпилированных классов
        val score = calculator.calculateForCompiled(classesDir, emptyList())

        assertEquals(100.0, score.total)
        assertEquals(ScoreGrade.EXCELLENT, score.getGrade())

        compiler.cleanup(root)
    }

    @Test
    fun `execution time is reasonable`() {
        val code = """
            package com.example.service;
            @Service
            public class TimingTest { 
                public void method1() { }
                public void method2() { }
                public void method3() { }
            }
        """.trimIndent()

        val startTime = System.currentTimeMillis()
        val score = calculator.calculate(code, "TimingTest", emptyList())
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue(elapsed < 10000, "Score calculation should complete within 10 seconds")
    }
}