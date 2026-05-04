package com.example.archassistant.integration

import com.example.archassistant.config.YamlConfig
import com.example.archassistant.model.*
import com.example.archassistant.service.*
import com.example.archassistant.util.CodeCompiler
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.nio.file.Path

/**
 * Интеграционный тест после Этапа 8:
 * Проверяет полный пайплайн: YAML config → правила → LLM генерация → валидация → Compliance Score
 *
 * Использует мок-ChatClient для симуляции LLM без реальных вызовов к API.
 */
@SpringBootTest
@Import(YamlConfig::class)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension::class)
class IntegrationAfterStage8Test {

    @TempDir
    lateinit var tempDir: Path

    @Autowired
    private lateinit var yamlMapper: ObjectMapper

    @Mock
    private lateinit var chatClient: ChatClient

    @Mock
    private lateinit var requestSpec: ChatClient.ChatClientRequestSpec

    @Mock
    private lateinit var responseSpec: ChatClient.CallResponseSpec

    @Captor
    private lateinit var systemPromptCaptor: ArgumentCaptor<String>

    @Captor
    private lateinit var userPromptCaptor: ArgumentCaptor<String>

    private lateinit var ruleRepository: YamlRuleRepository
    private lateinit var validator: DynamicRuleValidator
    private lateinit var scoreCalculator: ComplianceScoreCalculator
    private lateinit var orchestrator: LlmOrchestrator
    private lateinit var compiler: CodeCompiler

    @BeforeEach
    fun setUp() {
        ruleRepository = YamlRuleRepository(yamlMapper, tempDir.toString())
        compiler = CodeCompiler()
        validator = DynamicRuleValidator(compiler)
        scoreCalculator = ComplianceScoreCalculator(compiler)
        orchestrator = LlmOrchestrator(chatClient, ruleRepository)
    }

    private fun buildClasspath(vararg extraPaths: String): String {
        val cp = System.getProperty("java.class.path")
        val paths = cp.split(File.pathSeparator).toMutableList()
        paths.addAll(extraPaths)
        return paths.joinToString(File.pathSeparator)
    }

    private fun mockLlmResponse(responseCode: String) {
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(systemPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.user(userPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn(responseCode)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ТЕСТЫ ИЗ ЭТАПА 7 (переиспользуются, оставлены без изменений)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `full pipeline = yaml config + llm generation + validation passes for valid code`() {
        val config = RulesConfig(
            projectId = "integration.test",
            projectType = ProjectType.SPRING_BOOT,
            rules = listOf(
                ArchitecturalRule(
                    id = "no_controller_dep",
                    name = "Services should not depend on controllers",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.CRITICAL,
                    weight = 2.0,
                    enabled = true
                ),
                ArchitecturalRule(
                    id = "service_suffix",
                    name = "Services should have Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    severity = Severity.INFO,
                    weight = 0.5,
                    enabled = true
                )
            )
        )
        assertTrue(ruleRepository.save(config))

        val controllerCode = """
            package com.example.controller;
            public class UserController {
                public void handle() { }
            }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val fullClasspath = buildClasspath(controllerRoot.resolve("classes").toString())

        val validGeneratedCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                public void createUser(String name) { }
            }
        """.trimIndent()
        mockLlmResponse(validGeneratedCode)

        val generationResponse = orchestrator.generateCode(
            prompt = "Create UserService for User entity",
            projectId = "integration.test"
        )

        assertTrue(generationResponse.success)
        assertNotNull(generationResponse.data?.code)
        assertTrue(generationResponse.metadata.generationTimeMs > 0)

        val rules = ruleRepository.load("integration.test")!!.getEnabledRules()
        val validationResult = validator.validate(
            code = generationResponse.data!!.code,
            className = "UserService",
            rules = rules,
            classpath = fullClasspath
        )

        assertTrue(validationResult.passed)
        assertTrue(validationResult.violations.isEmpty())

        compiler.cleanup(controllerRoot)
    }

    @Test
    fun `full pipeline = generated code with dependency violation fails validation`() {
        val config = RulesConfig(
            projectId = "violation.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "no_controller_dep",
                    name = "Services should not depend on controllers",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.CRITICAL,
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val controllerCode = """
            package com.example.controller;
            public class UserController { }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val classpath = buildClasspath(controllerRoot.resolve("classes").toString())

        val invalidGeneratedCode = """
            package com.example.service;
            import com.example.controller.UserController;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                private UserController controller;
            }
        """.trimIndent()
        mockLlmResponse(invalidGeneratedCode)

        val generationResponse = orchestrator.generateCode(
            prompt = "Create UserService that uses UserController",
            projectId = "violation.test"
        )

        assertTrue(generationResponse.success)

        val rules = ruleRepository.load("violation.test")!!.getEnabledRules()
        val validationResult = validator.validate(
            code = generationResponse.data!!.code,
            className = "UserService",
            rules = rules,
            classpath = classpath
        )

        assertFalse(validationResult.passed)
        assertTrue(validationResult.violations.any { it.ruleId == "no_controller_dep" })
        assertEquals(Severity.CRITICAL, validationResult.violations.first().severity)

        compiler.cleanup(controllerRoot)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // НОВЫЕ ТЕСТЫ ДЛЯ ЭТАПА 8: COMPLIANCE SCORE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `compliance score = excellent for valid code with no violations`() {
        val config = RulesConfig(
            projectId = "score.excellent.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "service_suffix",
                    name = "Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    severity = Severity.INFO,
                    weight = 0.5,
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val validCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                public void createUser(String name) { }
            }
        """.trimIndent()

        val rules = ruleRepository.load("score.excellent.test")!!.getEnabledRules()
        val score = scoreCalculator.calculate(validCode, "UserService", rules, classpath = "")

        assertEquals(100.0, score.total)
        assertEquals(100.0, score.patternMatch)
        assertEquals(ScoreGrade.EXCELLENT, score.getGrade())
        assertTrue(score.violations.isEmpty())
    }

    @Test
    fun `compliance score = reduced for naming convention violation`() {
        val config = RulesConfig(
            projectId = "score.naming.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "service_suffix",
                    name = "Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    severity = Severity.INFO,
                    weight = 1.0,  // Высокий вес для чистого теста
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val invalidCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserLogic { }
        """.trimIndent()

        val classpath = buildClasspath()
        val rules = ruleRepository.load("score.naming.test")!!.getEnabledRules()
        val score = scoreCalculator.calculate(invalidCode, "UserLogic", rules, classpath = classpath)

        // PatternMatch < 100% из-за нарушения именования
        assertTrue(score.patternMatch < 100.0, "PatternMatch should detect naming violation")
        // Total < 100% из-за веса правила
        assertTrue(score.total < 100.0, "Total score should be reduced due to violation")
        // Нарушение зафиксировано
        assertTrue(score.violations.any { it.ruleId == "service_suffix" })
        // Grade не EXCELLENT
        assertNotEquals(ScoreGrade.EXCELLENT, score.getGrade())
    }

    @Test
    fun `compliance score = reduced for dependency violation with full classpath`() {
        // 1. Конфиг с правилом зависимости
        val config = RulesConfig(
            projectId = "score.dep.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "no_controller_dep",
                    name = "No controller dependency",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.CRITICAL,
                    weight = 1.0,
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        // 2. Компилируем контроллер для classpath
        val controllerCode = """
            package com.example.controller;
            public class UserController { }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val classpath = buildClasspath(controllerRoot.resolve("classes").toString())

        // 3. Код сервиса с нарушением
        val violatingCode = """
            package com.example.service;
            import com.example.controller.UserController;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                private UserController controller;
            }
        """.trimIndent()

        // 4. Расчёт скор
        val rules = ruleRepository.load("score.dep.test")!!.getEnabledRules()
        val score = scoreCalculator.calculate(
            code = violatingCode,
            className = "UserService",
            rules = rules,
            classpath = classpath
        )

        // 5. Проверки
        assertTrue(score.dependencyCorrect < 100.0, "DependencyCorrect should detect violation")
        assertTrue(score.total < 100.0, "Total score should be reduced")
        assertTrue(score.violations.any { it.ruleId == "no_controller_dep" })
        assertEquals(Severity.CRITICAL, score.violations.first { it.ruleId == "no_controller_dep" }.severity)

        compiler.cleanup(controllerRoot)
    }

    @Test
    fun `compliance score respects custom weights`() {
        val config = RulesConfig(
            projectId = "score.weights.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "naming",
                    name = "Naming rule",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    weight = 2.0,  // Высокий вес
                    enabled = true
                ),
                ArchitecturalRule(
                    id = "dep",
                    name = "Dependency rule",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    weight = 0.5,  // Низкий вес
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        // Код с нарушением именования (высокий вес) но без нарушения зависимости
        val code = """
            package com.example.service;
            public class BadName { }
        """.trimIndent()

        val rules = ruleRepository.load("score.weights.test")!!.getEnabledRules()
        val customWeights = ScoreWeights(rulesPass = 0.5, patternMatch = 2.0, dependencyCorrect = 0.5)
        val score = scoreCalculator.calculate(code, "BadName", rules, weights = customWeights)

        // PatternMatch имеет больший вес, поэтому сильнее влияет на total
        // Формула: (0.5×100 + 2.0×0 + 0.5×100) / 3.0 = 33.33
        val expectedTotal = (0.5 * 100.0 + 2.0 * score.patternMatch + 0.5 * 100.0) / 3.0
        assertEquals(expectedTotal, score.total, 0.01)
    }

    @Test
    fun `compliance score includes detailed violations`() {
        val config = RulesConfig(
            projectId = "score.violations.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "prefix_test",
                    name = "Prefix test",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_PREFIX,
                    pattern = "Good",
                    severity = Severity.WARNING,
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val code = """
            package com.example.service;
            class BadService { }
            class GoodService { }
        """.trimIndent()

        val rules = ruleRepository.load("score.violations.test")!!.getEnabledRules()
        val score = scoreCalculator.calculate(code, "MultiClass", rules)

        // Нарушение зафиксировано с указанием класса
        val violation = score.violations.firstOrNull { it.ruleId == "prefix_test" }
        assertNotNull(violation)
        assertTrue(violation!!.className.contains("BadService"), "Violation should specify BadService")
        assertEquals(Severity.WARNING, violation.severity)
        assertTrue(violation.description.contains("Good"), "Description should mention expected prefix")
    }

    @Test
    fun `isPassing returns correct result based on threshold`() {
        val config = RulesConfig(
            projectId = "score.threshold.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "strict",
                    name = "Strict rule",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    weight = 10.0,  // Очень высокий вес
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val invalidCode = """
            package com.example.service;
            public class BadName { }
        """.trimIndent()

        val rules = ruleRepository.load("score.threshold.test")!!.getEnabledRules()

        // Низкий порог — код проходит
        assertTrue(scoreCalculator.isPassing(invalidCode, "BadName", rules, threshold = 30.0))
        // Высокий порог — код не проходит
        assertFalse(scoreCalculator.isPassing(invalidCode, "BadName", rules, threshold = 90.0))
    }

    @Test
    fun `full pipeline = score calculation after llm generation`() {
        // 1. Конфиг с правилами
        val config = RulesConfig(
            projectId = "pipeline.score.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix",
                    name = "Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    severity = Severity.INFO,
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        // 2. Мок-ответ: валидный код
        val validCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService { }
        """.trimIndent()
        mockLlmResponse(validCode)

        // 3. Генерация
        val generationResponse = orchestrator.generateCode(
            prompt = "Create UserService",
            projectId = "pipeline.score.test"
        )
        assertTrue(generationResponse.success)

        // 4. Расчёт скор для сгенерированного кода
        val rules = ruleRepository.load("pipeline.score.test")!!.getEnabledRules()
        val score = scoreCalculator.calculate(
            code = generationResponse.data!!.code,
            className = "UserService",
            rules = rules,
            classpath = ""
        )

        // 5. Валидный код должен получить высокий скор
        assertEquals(100.0, score.total)
        assertEquals(ScoreGrade.EXCELLENT, score.getGrade())
        assertTrue(score.violations.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ВСПОМОГАТЕЛЬНЫЕ ТЕСТЫ (из этапа 7, оставлены для полноты)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `disabled rules are ignored during score calculation`() {
        val config = RulesConfig(
            projectId = "score.disabled.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "disabled",
                    name = "Disabled",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    enabled = false
                )
            )
        )
        ruleRepository.save(config)

        val code = """
            package com.example.service;
            public class BadName { }
        """.trimIndent()

        val rules = ruleRepository.load("score.disabled.test")!!.getEnabledRules()
        assertTrue(rules.isEmpty(), "Disabled rules should not be loaded as enabled")

        val score = scoreCalculator.calculate(code, "BadName", rules)
        assertEquals(100.0, score.total, "Disabled rules should not affect score")
    }

    @Test
    fun `empty rules return perfect score`() {
        val code = """
            package com.example.any;
            public class AnyClass { }
        """.trimIndent()

        val score = scoreCalculator.calculate(code, "AnyClass", emptyList())
        assertEquals(100.0, score.total)
        assertEquals(ScoreGrade.EXCELLENT, score.getGrade())
    }
}