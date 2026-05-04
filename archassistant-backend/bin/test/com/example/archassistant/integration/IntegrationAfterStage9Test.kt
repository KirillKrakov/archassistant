package com.example.archassistant.integration

import com.example.archassistant.config.YamlConfig
import com.example.archassistant.dto.CodeGenerationRequest
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
 * Интеграционный тест после Этапа 9:
 * Проверяет полный пайплайн: YAML config → правила → Pre-Strategy генерация → валидация → Compliance Score
 *
 * Использует мок-ChatClient для симуляции LLM без реальных вызовов к API.
 */
@SpringBootTest
@Import(YamlConfig::class)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension::class)
class IntegrationAfterStage9Test {

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
    private lateinit var preStrategy: PreGenerationStrategy
    private lateinit var compiler: CodeCompiler

    @BeforeEach
    fun setUp() {
        ruleRepository = YamlRuleRepository(yamlMapper, tempDir.toString())
        compiler = CodeCompiler()
        validator = DynamicRuleValidator(compiler)
        scoreCalculator = ComplianceScoreCalculator(compiler)
        orchestrator = LlmOrchestrator(chatClient, ruleRepository)
        // PreStrategy использует тот же orchestrator (переиспользование логики)
        preStrategy = PreGenerationStrategy(
            llmOrchestrator = orchestrator,
            ruleRepository = ruleRepository,
            scoreCalculator = scoreCalculator,
            warningThreshold = 70.0
        )
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
    // ТЕСТЫ ИЗ ЭТАПОВ 7-8 (переиспользуются, оставлены без изменений)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `stage7 full pipeline = yaml config + llm generation + validation passes for valid code`() {
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
    fun `stage7 full pipeline = generated code with dependency violation fails validation`() {
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

    @Test
    fun `stage8 compliance score = excellent for valid code with no violations`() {
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
        assertEquals(ScoreGrade.EXCELLENT, ScoreGrade.fromScore(score.total))
        assertTrue(score.violations.isEmpty())
    }

    @Test
    fun `stage8 compliance score = reduced for naming convention violation`() {
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
                    weight = 1.0,
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

        assertTrue(score.patternMatch < 100.0, "PatternMatch should detect naming violation")
        assertTrue(score.total < 100.0, "Total score should be reduced due to violation")
        assertTrue(score.violations.any { it.ruleId == "service_suffix" })
        assertNotEquals(ScoreGrade.EXCELLENT, ScoreGrade.fromScore(score.total))
    }

    @Test
    fun `stage8 compliance score = reduced for dependency violation with full classpath`() {
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

        val controllerCode = """
            package com.example.controller;
            public class UserController { }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val classpath = buildClasspath(controllerRoot.resolve("classes").toString())

        val violatingCode = """
            package com.example.service;
            import com.example.controller.UserController;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                private UserController controller;
            }
        """.trimIndent()

        val rules = ruleRepository.load("score.dep.test")!!.getEnabledRules()
        val score = scoreCalculator.calculate(
            code = violatingCode,
            className = "UserService",
            rules = rules,
            classpath = classpath
        )

        assertTrue(score.dependencyCorrect < 100.0, "DependencyCorrect should detect violation")
        assertTrue(score.total < 100.0, "Total score should be reduced")
        assertTrue(score.violations.any { it.ruleId == "no_controller_dep" })
        assertEquals(Severity.CRITICAL, score.violations.first { it.ruleId == "no_controller_dep" }.severity)

        compiler.cleanup(controllerRoot)
    }

    @Test
    fun `stage8 compliance score respects custom weights`() {
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
                    weight = 2.0,
                    enabled = true
                ),
                ArchitecturalRule(
                    id = "dep",
                    name = "Dependency rule",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    weight = 0.5,
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val code = """
            package com.example.service;
            public class BadName { }
        """.trimIndent()

        val rules = ruleRepository.load("score.weights.test")!!.getEnabledRules()
        val customWeights = ScoreWeights(rulesPass = 0.5, patternMatch = 2.0, dependencyCorrect = 0.5)
        val score = scoreCalculator.calculate(code, "BadName", rules, weights = customWeights)

        // PatternMatch имеет больший вес, поэтому сильнее влияет на total
        val expectedTotal = (0.5 * 100.0 + 2.0 * score.patternMatch + 0.5 * 100.0) / 3.0
        assertEquals(expectedTotal, score.total, 0.01)
    }

    @Test
    fun `stage8 compliance score includes detailed violations`() {
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

        val violation = score.violations.firstOrNull { it.ruleId == "prefix_test" }
        assertNotNull(violation)
        assertTrue(violation!!.className.contains("BadService"), "Violation should specify BadService")
        assertEquals(Severity.WARNING, violation.severity)
        assertTrue(violation.description.contains("Good"), "Description should mention expected prefix")
    }

    @Test
    fun `stage8 isPassing returns correct result based on threshold`() {
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
                    weight = 10.0,
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

        assertTrue(scoreCalculator.isPassing(invalidCode, "BadName", rules, threshold = 30.0))
        assertFalse(scoreCalculator.isPassing(invalidCode, "BadName", rules, threshold = 90.0))
    }

    @Test
    fun `stage8 full pipeline = score calculation after llm generation`() {
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

        val validCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService { }
        """.trimIndent()
        mockLlmResponse(validCode)

        val generationResponse = orchestrator.generateCode(
            prompt = "Create UserService",
            projectId = "pipeline.score.test"
        )
        assertTrue(generationResponse.success)

        val rules = ruleRepository.load("pipeline.score.test")!!.getEnabledRules()
        val score = scoreCalculator.calculate(
            code = generationResponse.data!!.code,
            className = "UserService",
            rules = rules,
            classpath = ""
        )

        assertEquals(100.0, score.total)
        assertEquals(ScoreGrade.EXCELLENT, ScoreGrade.fromScore(score.total))
        assertTrue(score.violations.isEmpty())
    }

    @Test
    fun `stage8 disabled rules are ignored during score calculation`() {
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
    fun `stage8 empty rules return perfect score`() {
        val code = """
            package com.example.any;
            public class AnyClass { }
        """.trimIndent()

        val score = scoreCalculator.calculate(code, "AnyClass", emptyList())
        assertEquals(100.0, score.total)
        assertEquals(ScoreGrade.EXCELLENT, ScoreGrade.fromScore(score.total))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // НОВЫЕ ТЕСТЫ ДЛЯ ЭТАПА 9: PRE-STRATEGY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `stage9 pre-strategy injects rules into system prompt and generates code in 1 iteration`() {
        val config = RulesConfig(
            projectId = "pre.basic.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "no_ctrl_dep",
                    name = "No controller dependency",
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

        val expectedCode = "public class UserService { }"
        mockLlmResponse(expectedCode)

        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "pre.basic.test",
            collectMetrics = false
        )

        val response = preStrategy.generate(request)

        assertTrue(response.success)
        assertEquals(expectedCode, response.data?.code)
        assertEquals(StrategyType.PRE, response.data?.strategy)
        assertEquals(1, response.data?.iterations) // Pre: всегда 1 итерация

        // Проверяем, что правила попали в system prompt
        val systemPrompt = systemPromptCaptor.value
        assertTrue(systemPrompt.contains("No controller dependency"))
        assertTrue(systemPrompt.contains("АРХИТЕКТУРНЫЕ ПРАВИЛА"))
    }

    @Test
    fun `stage9 pre-strategy includes warnings about strategy limitations`() {
        val config = RulesConfig(
            projectId = "pre.warnings.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "test",
                    name = "Test rule",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY
                )
            )
        )
        ruleRepository.save(config)
        mockLlmResponse("code")

        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "pre.warnings.test",
            collectMetrics = false
        )

        val response = preStrategy.generate(request)

        assertTrue(response.success)
        val warnings = response.data?.warnings
        assertNotNull(warnings)
        assertTrue(warnings!!.any { it.contains("Pre-Strategy") && it.contains("not enforced") })
    }

    @Test
    fun `stage9 pre-strategy with collectMetrics performs validation and returns score`() {
        val config = RulesConfig(
            projectId = "pre.metrics.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix",
                    name = "Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val generatedCode = "public class UserService { }"
        mockLlmResponse(generatedCode)

        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "pre.metrics.test",
            collectMetrics = true,
            expectedClassName = "UserService",
            classpath = ""
        )

        val response = preStrategy.generate(request)

        assertTrue(response.success)
        assertNotNull(response.data?.score)
        assertTrue(response.data?.score?.total!! > 0)
    }

    @Test
    fun `stage9 pre-strategy skips validation when expectedClassName is null and extraction fails`() {
        val config = RulesConfig(
            projectId = "pre.skip.test",
            rules = emptyList()
        )
        ruleRepository.save(config)

        // Код без объявления класса — извлечение имени должно вернуть null
        val generatedCode = "public void someMethod() { }"
        mockLlmResponse(generatedCode)

        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "pre.skip.test",
            collectMetrics = true,
            expectedClassName = null
        )

        val response = preStrategy.generate(request)

        assertTrue(response.success)
        assertNull(response.data?.score, "Score should be null when validation is skipped")
        val warnings = response.data?.warnings
        assertNotNull(warnings)
        assertTrue(warnings!!.any { it.contains("Could not extract class name") })
    }

    @Test
    fun `stage9 pre-strategy includes low score warning when score below threshold`() {
        val config = RulesConfig(
            projectId = "pre.low.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix",
                    name = "Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    enabled = true,
                    weight = 2.0
                )
            )
        )
        ruleRepository.save(config)

        val invalidCode = """
        package com.example.service;
        public class UserLogic { }
    """.trimIndent()
        mockLlmResponse(invalidCode)

        // Создаём стратегию с порогом выше ожидаемого скора (75%)
        val highThresholdStrategy = PreGenerationStrategy(
            llmOrchestrator = orchestrator,
            ruleRepository = ruleRepository,
            scoreCalculator = scoreCalculator,
            warningThreshold = 80.0
        )

        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "pre.low.test",
            collectMetrics = true,
            expectedClassName = "UserLogic"
        )

        val response = highThresholdStrategy.generate(request)

        assertTrue(response.success)
        val warnings = response.data?.warnings
        assertNotNull(warnings)
        assertTrue(warnings!!.any { it.contains("Compliance Score") && it.contains("below threshold") })
    }

    @Test
    fun `stage9 pre-strategy records generation and validation times separately`() {
        val config = RulesConfig(projectId = "pre.timing.test", rules = emptyList())
        ruleRepository.save(config)

        // Имитация задержки генерации (оставляем, т.к. chatClient мок)
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        doAnswer {
            Thread.sleep(50)
            responseSpec
        }.whenever(requestSpec).call()
        whenever(responseSpec.content()).thenReturn("code")

        // Убираем doAnswer для scoreCalculator, оставляем реальный вызов
        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "pre.timing.test",
            collectMetrics = true,
            expectedClassName = "TestClass"
        )

        val response = preStrategy.generate(request)

        assertTrue(response.success)
        assertTrue(response.metadata.generationTimeMs >= 50)
        // validationTimeMs будет реальным (скорее всего < 10 мс), не проверяем на >=30
        assertTrue(response.metadata.validationTimeMs >= 0)
        assertEquals(
            response.metadata.generationTimeMs + response.metadata.validationTimeMs,
            response.metadata.totalTimeMs
        )
    }

    @Test
    fun `stage9 pre-strategy handles empty prompt with error without calling LLM`() {
        val response = preStrategy.generate(
            CodeGenerationRequest(prompt = "", projectId = "test")
        )

        assertFalse(response.success)
        assertEquals("INVALID_PROMPT", response.error?.code)
        verify(chatClient, never()).prompt()
    }

    @Test
    fun `stage9 pre-strategy retries on temporary LLM error and succeeds`() {
        val config = RulesConfig(projectId = "pre.retry.test", rules = emptyList())
        ruleRepository.save(config)

        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        // Первые два вызова кидают исключение, третий возвращает ответ
        doThrow(RuntimeException("Temporary error"))
            .doThrow(RuntimeException("Temporary error"))
            .doReturn(responseSpec)
            .whenever(requestSpec).call()
        whenever(responseSpec.content()).thenReturn("final code")

        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "pre.retry.test",
            maxIterations = 3,
            collectMetrics = false
        )

        val response = preStrategy.generate(request)

        assertTrue(response.success)
        assertEquals("final code", response.data?.code)
        verify(requestSpec, times(3)).call()
    }

    @Test
    fun `stage9 pre-strategy returns error after exhausting retries`() {
        val config = RulesConfig(projectId = "pre.error.test", rules = emptyList())
        ruleRepository.save(config)

        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        doThrow(RuntimeException("Persistent error")).whenever(requestSpec).call()

        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "pre.error.test",
            maxIterations = 2,
            collectMetrics = false
        )

        val response = preStrategy.generate(request)

        assertFalse(response.success)
        assertEquals("LLM_ERROR", response.error?.code)
        verify(requestSpec, times(2)).call()
    }

    @Test
    fun `stage9 full pipeline = pre-strategy generation + compliance score for valid code`() {
        val config = RulesConfig(
            projectId = "pre.pipeline.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix",
                    name = "Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val validCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService { }
        """.trimIndent()
        mockLlmResponse(validCode)

        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "pre.pipeline.test",
            collectMetrics = true,
            expectedClassName = "UserService"
        )

        val response = preStrategy.generate(request)

        assertTrue(response.success)
        assertNotNull(response.data?.score)
        assertEquals(100.0, response.data?.score?.total)
        assertEquals(ScoreGrade.EXCELLENT, ScoreGrade.fromScore(response.data?.score?.total!!))
        assertTrue(response.data?.score?.violations?.isEmpty() == true)
    }

    @Test
    fun `stage9 full pipeline = pre-strategy generation + compliance score detects naming violation`() {
        val config = RulesConfig(
            projectId = "pre.pipeline.violation.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix",
                    name = "Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    weight = 1.0,
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
        mockLlmResponse(invalidCode)

        val request = CodeGenerationRequest(
            prompt = "Create service with wrong name",
            projectId = "pre.pipeline.violation.test",
            collectMetrics = true,
            expectedClassName = "UserLogic"
        )

        val response = preStrategy.generate(request)

        assertTrue(response.success)
        assertNotNull(response.data?.score)
        assertTrue(response.data?.score?.total!! < 100.0, "Score should be reduced due to naming violation")
        assertTrue(response.data?.score?.violations?.any { it.ruleId == "suffix" } == true)
        // Pre-стратегия не перегенерирует, поэтому код с нарушением возвращается как есть
        assertEquals("UserLogic", response.data?.code?.substringAfter("class ")?.substringBefore(' '))
    }
}