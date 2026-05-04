package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class HybridGenerationStrategyTest {

    @Mock
    private lateinit var llmOrchestrator: LlmOrchestrator

    @Mock
    private lateinit var ruleRepository: YamlRuleRepository

    @Mock
    private lateinit var scoreCalculator: ComplianceScoreCalculator

    private lateinit var strategy: HybridGenerationStrategy

    @BeforeEach
    fun setUp() {
        strategy = HybridGenerationStrategy(
            llmOrchestrator = llmOrchestrator,
            ruleRepository = ruleRepository,
            scoreCalculator = scoreCalculator,
            warningThreshold = 70.0
        )
    }

    @Test
    fun `hybrid succeeds on first iteration when code is valid and rules in prompt help`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "test",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = "UserService"
        )

        val rules = listOf(
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

        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = rules)
        )

        val validCode = "public class UserService { }"
        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any())).thenReturn(validCode)

        val perfectScore = ComplianceScore(
            total = 100.0,
            rulesPass = 100.0,
            patternMatch = 100.0,
            dependencyCorrect = 100.0,
            violations = emptyList()
        )
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any())).thenReturn(perfectScore)

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success)
        assertEquals(validCode, response.data?.code)
        assertEquals(StrategyType.HYBRID, response.data?.strategy)
        assertEquals(1, response.data?.iterations) // Успех с первой попытки
        assertEquals(100.0, response.data?.score?.total)
        verify(llmOrchestrator, times(1)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `hybrid retries when code violates rules despite rules in prompt`() {
        // Arrange: Даже с правилами в промпте LLM может ошибиться
        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "test",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = "UserService"
        )

        val rules = listOf(
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

        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = rules)
        )

        // Первая итерация: код с нарушением (несмотря на правила в промпте)
        val invalidCode = "public class UserLogic { }"
        // Вторая итерация: исправленный код
        val validCode = "public class UserService { }"

        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any()))
            .thenReturn(invalidCode)
            .thenReturn(validCode)

        // Скор для первой итерации (низкий)
        val lowScore = ComplianceScore(
            total = 50.0,
            rulesPass = 100.0,
            patternMatch = 0.0,
            dependencyCorrect = 100.0,
            violations = listOf(
                Violation(
                    ruleId = "suffix",
                    description = "Class `UserLogic` should end with `Service`",
                    className = "UserLogic",
                    severity = Severity.INFO
                )
            )
        )
        // Скор для второй итерации (высокий)
        val highScore = ComplianceScore(
            total = 100.0,
            rulesPass = 100.0,
            patternMatch = 100.0,
            dependencyCorrect = 100.0,
            violations = emptyList()
        )

        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any()))
            .thenReturn(lowScore)
            .thenReturn(highScore)

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success)
        assertEquals(validCode, response.data?.code)
        assertEquals(StrategyType.HYBRID, response.data?.strategy)
        assertEquals(2, response.data?.iterations) // Две итерации
        assertEquals(100.0, response.data?.score?.total)
        verify(llmOrchestrator, times(2)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `hybrid returns best result after exhausting iterations`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            maxIterations = 2,
            collectMetrics = true,
            expectedClassName = "BadService"
        )

        val rules = emptyList<ArchitecturalRule>()
        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = rules)
        )

        val badCode = "public class BadService { }"
        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any())).thenReturn(badCode)

        val lowScore = ComplianceScore(
            total = 40.0,
            rulesPass = 0.0,
            patternMatch = 50.0,
            dependencyCorrect = 70.0,
            violations = listOf(
                Violation(
                    ruleId = "some_rule",
                    description = "Some violation",
                    className = "BadService",
                    severity = Severity.ERROR
                )
            )
        )
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any())).thenReturn(lowScore)

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success) // Успех, но с предупреждением
        assertEquals(badCode, response.data?.code)
        assertEquals(StrategyType.HYBRID, response.data?.strategy)
        assertEquals(2, response.data?.iterations)
        assertEquals(40.0, response.data?.score?.total)
        assertTrue(response.data?.warnings?.any { it.contains("Could not achieve compliance") } == true)
        verify(llmOrchestrator, times(2)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `hybrid includes rules in system prompt on first iteration`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            maxIterations = 1,
            expectedClassName = "TestService"
        )

        val rules = listOf(
            ArchitecturalRule(
                id = "test",
                name = "Test rule",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY
            )
        )

        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = rules)
        )

        // FIXED: Используем doAnswer для захвата аргументов вместо ArgumentCaptor
        var capturedSystemPrompt: String? = null
        var capturedUserPrompt: String? = null

        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any())).thenAnswer { invocation ->
            // Индексы аргументов: 0 = systemPrompt, 1 = userPrompt, 2 = maxRetries
            capturedSystemPrompt = invocation.getArgument(0)
            capturedUserPrompt = invocation.getArgument(1)
            "code" // Возвращаемое значение
        }

        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any())).thenReturn(
            ComplianceScore(total = 100.0, rulesPass = 100.0, patternMatch = 100.0, dependencyCorrect = 100.0)
        )

        // Act
        strategy.generate(request)

        // Assert
        assertNotNull(capturedSystemPrompt, "System prompt should be captured")
        assertTrue(capturedSystemPrompt!!.contains("Test rule"), "System prompt should contain rule name")
        assertTrue(capturedSystemPrompt!!.contains("АРХИТЕКТУРНЫЕ ПРАВИЛА"), "System prompt should contain rules section header")

        // Опционально: проверяем, что userPrompt не содержит правил (нет дублирования)
        assertNotNull(capturedUserPrompt)
        assertFalse(capturedUserPrompt!!.contains("Test rule"), "User prompt should not duplicate rules")

        // Проверяем, что LLM вызывался ровно 1 раз
        verify(llmOrchestrator, times(1)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `hybrid handles validation exception gracefully`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            maxIterations = 2,
            collectMetrics = true,
            expectedClassName = "TestService"
        )

        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = emptyList())
        )

        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any())).thenReturn("code")
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("Compilation failed: class not found"))

        // Act
        val response = strategy.generate(request)

        // Assert
        assertFalse(response.success)
        assertEquals("VALIDATION_ERROR", response.error?.code)
        assertTrue(response.error?.message?.contains("Compilation failed") == true)
        verify(llmOrchestrator, times(1)).generateCodeRaw(any(), any(), any())
        verify(scoreCalculator, times(1)).calculate(any(), any(), any(), any(), any())
    }

    @Test
    fun `hybrid accumulates generation and validation times across iterations`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "test",
            maxIterations = 2,
            collectMetrics = true,
            expectedClassName = "TestClass"
        )

        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = emptyList())
        )

        // Имитация задержек
        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any()))
            .thenAnswer { Thread.sleep(50); "code1" }
            .thenAnswer { Thread.sleep(50); "code2" }

        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any()))
            .thenAnswer { Thread.sleep(30); ComplianceScore(total = 50.0, rulesPass = 100.0, patternMatch = 0.0, dependencyCorrect = 100.0) }
            .thenAnswer { Thread.sleep(30); ComplianceScore(total = 100.0, rulesPass = 100.0, patternMatch = 100.0, dependencyCorrect = 100.0) }

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success)
        assertTrue(response.metadata.generationTimeMs >= 100) // 2 × 50ms
        assertTrue(response.metadata.validationTimeMs >= 60)   // 2 × 30ms
        assertEquals(
            response.metadata.generationTimeMs + response.metadata.validationTimeMs,
            response.metadata.totalTimeMs
        )
    }

    @Test
    fun `hybrid respects maxIterations and does not exceed`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = "TestService"
        )

        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = emptyList())
        )

        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any())).thenReturn("code")

        val lowScore = ComplianceScore(
            total = 30.0,
            rulesPass = 0.0,
            patternMatch = 30.0,
            dependencyCorrect = 60.0,
            violations = listOf(
                Violation(
                    ruleId = "test",
                    description = "Always failing",
                    className = "TestService",
                    severity = Severity.ERROR
                )
            )
        )
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any())).thenReturn(lowScore)

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success)
        assertEquals(3, response.data?.iterations) // Ровно 3 итерации
        verify(llmOrchestrator, times(3)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `hybrid skips validation when class name extraction fails`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            collectMetrics = true,
            expectedClassName = null
        )

        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = emptyList())
        )

        val codeWithoutClass = "public void someMethod() { }"
        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any())).thenReturn(codeWithoutClass)

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success)
        assertTrue(response.data?.warnings?.any { it.contains("Could not extract class name") } == true)
        verify(scoreCalculator, never()).calculate(any(), any(), any(), any(), any())
    }

    @Test
    fun `hybrid handles empty prompt with error`() {
        // Act
        val response = strategy.generate(
            CodeGenerationRequest(prompt = "", projectId = "test")
        )

        // Assert
        assertFalse(response.success)
        assertEquals("INVALID_PROMPT", response.error?.code)
        verify(llmOrchestrator, never()).generateCodeRaw(any(), any(), any())
    }
}