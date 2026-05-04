package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.ai.chat.client.ChatClient

@ExtendWith(MockitoExtension::class)
class PreGenerationStrategyTest {

    @Mock
    private lateinit var chatClient: ChatClient

    @Mock
    private lateinit var requestSpec: ChatClient.ChatClientRequestSpec

    @Mock
    private lateinit var responseSpec: ChatClient.CallResponseSpec

    @Mock
    private lateinit var ruleRepository: YamlRuleRepository

    @Mock
    private lateinit var scoreCalculator: ComplianceScoreCalculator

    @Captor
    private lateinit var systemPromptCaptor: ArgumentCaptor<String>

    @Captor
    private lateinit var userPromptCaptor: ArgumentCaptor<String>

    private lateinit var llmOrchestrator: LlmOrchestrator
    private lateinit var strategy: PreGenerationStrategy

    @BeforeEach
    fun setUp() {
        // Реальный оркестратор с мок-клиентом
        llmOrchestrator = LlmOrchestrator(chatClient, ruleRepository)
        strategy = PreGenerationStrategy(
            llmOrchestrator = llmOrchestrator,
            ruleRepository = ruleRepository,
            scoreCalculator = scoreCalculator,
            warningThreshold = 70.0
        )
    }

    @Test
    fun `generate calls LlmOrchestrator with formatted prompts and rules`() {
        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "test-project",
            strategy = StrategyType.PRE,
            collectMetrics = false
        )
        val rules = listOf(
            ArchitecturalRule(
                id = "rule_001",
                name = "Services should not depend on controllers",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY,
                enabled = true
            )
        )
        whenever(ruleRepository.load("test-project")).thenReturn(
            RulesConfig(projectId = "test-project", rules = rules)
        )
        val expectedCode = "public class UserService { }"
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(systemPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.user(userPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn(expectedCode)

        val response = strategy.generate(request)

        assertTrue(response.success)
        assertEquals(expectedCode, response.data?.code)
        assertEquals(StrategyType.PRE, response.data?.strategy)
        assertEquals(1, response.data?.iterations)

        val systemPrompt = systemPromptCaptor.value
        assertTrue(systemPrompt.contains("Services should not depend on controllers"))
        assertTrue(systemPrompt.contains("АРХИТЕКТУРНЫЕ ПРАВИЛА"))
        val userPrompt = userPromptCaptor.value
        assertTrue(userPrompt.contains("Create UserService"))
    }

    @Test
    fun `generate includes warnings about Pre-Strategy limitations`() {
        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "test",
            collectMetrics = false
        )
        val rules = listOf(
            ArchitecturalRule(
                id = "rule",
                name = "Dummy rule",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY
            )
        )
        whenever(ruleRepository.load("test")).thenReturn(RulesConfig(projectId = "test", rules = rules))
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn("code")

        val response = strategy.generate(request)

        assertTrue(response.success)
        val warnings = response.data?.warnings
        assertNotNull(warnings)
        assertTrue(warnings!!.any { it.contains("Pre-Strategy") && it.contains("not enforced") })
    }

    @Test
    fun `generate with collectMetrics and expectedClassName performs validation`() {
        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "test",
            collectMetrics = true,
            expectedClassName = "UserService",
            classpath = "/some/classpath"
        )
        val rules = emptyList<ArchitecturalRule>()
        whenever(ruleRepository.load("test")).thenReturn(RulesConfig(projectId = "test", rules = rules))

        val generatedCode = "public class UserService { }"
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn(generatedCode)

        val mockScore = ComplianceScore(
            total = 85.0,
            rulesPass = 100.0,
            patternMatch = 80.0,
            dependencyCorrect = 75.0
        )
        whenever(scoreCalculator.calculate(
            code = eq(generatedCode),
            className = eq("UserService"),
            rules = eq(rules),
            weights = eq(ScoreWeights()),
            classpath = eq("/some/classpath")
        )).thenReturn(mockScore)

        val response = strategy.generate(request)

        assertTrue(response.success)
        assertNotNull(response.data?.score)
        assertEquals(85.0, response.data?.score?.total)
        verify(scoreCalculator).calculate(
            code = eq(generatedCode),
            className = eq("UserService"),
            rules = eq(rules),
            weights = eq(ScoreWeights()),
            classpath = eq("/some/classpath")
        )
    }

    @Test
    fun `generate with collectMetrics but no expectedClassName and class name extraction fails skips validation`() {
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            collectMetrics = true,
            expectedClassName = null
        )
        whenever(ruleRepository.load("test")).thenReturn(RulesConfig(projectId = "test", rules = emptyList()))
        val generatedCode = "public void someMethod() { }" // нет класса
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn(generatedCode)

        val response = strategy.generate(request)

        assertTrue(response.success)
        assertNull(response.data?.score)
        val warnings = response.data?.warnings
        assertNotNull(warnings)
        assertTrue(warnings!!.any { it.contains("Could not extract class name") })
        verify(scoreCalculator, never()).calculate(any(), any(), any(), any(), any())
    }

    @Test
    fun `generate includes low score warning when score below threshold`() {
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            collectMetrics = true,
            expectedClassName = "BadService"
        )
        whenever(ruleRepository.load("test")).thenReturn(RulesConfig(projectId = "test", rules = emptyList()))
        val generatedCode = "public class BadService { }"
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn(generatedCode)

        val lowScore = ComplianceScore(
            total = 65.0,
            rulesPass = 100.0,
            patternMatch = 50.0,
            dependencyCorrect = 50.0
        )
        whenever(scoreCalculator.calculate(
            code = eq(generatedCode),
            className = eq("BadService"),
            rules = eq(emptyList()),
            weights = eq(ScoreWeights()),
            classpath = eq("")
        )).thenReturn(lowScore)

        val response = strategy.generate(request)

        assertTrue(response.success)
        val warnings = response.data?.warnings
        assertNotNull(warnings)
        assertTrue(warnings!!.any { it.contains("Compliance Score 65.0% is below threshold 70.0%") })
    }

    @Test
    fun `generate handles empty prompt with error without calling LLM`() {
        val response = strategy.generate(
            CodeGenerationRequest(prompt = "", projectId = "test")
        )
        assertFalse(response.success)
        assertEquals("INVALID_PROMPT", response.error?.code)
        verify(chatClient, never()).prompt()
        verify(scoreCalculator, never()).calculate(any(), any(), any(), any(), any())
    }

    @Test
    fun `generate retries on LLM error and returns success eventually`() {
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            maxIterations = 3,
            collectMetrics = false
        )
        whenever(ruleRepository.load("test")).thenReturn(RulesConfig(projectId = "test", rules = emptyList()))

        // Настройка моков точно как в LlmOrchestratorTest
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        // Первые два вызова кидают исключение, третий возвращает responseSpec
        doThrow(RuntimeException("Temporary error"))
            .doThrow(RuntimeException("Temporary error"))
            .doReturn(responseSpec)
            .whenever(requestSpec).call()
        whenever(responseSpec.content()).thenReturn("final code")

        val response = strategy.generate(request)

        assertTrue(response.success)
        assertEquals("final code", response.data?.code)
        verify(requestSpec, times(3)).call()
    }

    @Test
    fun `generate returns error after exhausting retries`() {
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            maxIterations = 2,
            collectMetrics = false
        )
        whenever(ruleRepository.load("test")).thenReturn(RulesConfig(projectId = "test", rules = emptyList()))

        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        doThrow(RuntimeException("Persistent error")).whenever(requestSpec).call()

        val response = strategy.generate(request)

        assertFalse(response.success)
        assertEquals("LLM_ERROR", response.error?.code)
        verify(requestSpec, times(2)).call()
    }

    @Test
    fun `generation and validation times are recorded separately`() {
        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "test",
            collectMetrics = true,
            expectedClassName = "TestClass"
        )
        whenever(ruleRepository.load("test")).thenReturn(RulesConfig(projectId = "test", rules = emptyList()))

        // Имитация задержки генерации
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        doAnswer {
            Thread.sleep(50)
            responseSpec
        }.whenever(requestSpec).call()
        whenever(responseSpec.content()).thenReturn("code")

        // Имитация задержки валидации
        val mockScore = ComplianceScore(
            total = 90.0,
            rulesPass = 100.0,
            patternMatch = 90.0,
            dependencyCorrect = 80.0
        )
        doAnswer {
            Thread.sleep(30)
            mockScore
        }.whenever(scoreCalculator).calculate(any(), any(), any(), any(), any())

        val response = strategy.generate(request)

        assertTrue(response.success)
        assertTrue(response.metadata.generationTimeMs >= 50)
        assertTrue(response.metadata.validationTimeMs >= 30)
        assertEquals(
            response.metadata.generationTimeMs + response.metadata.validationTimeMs,
            response.metadata.totalTimeMs
        )
    }
}