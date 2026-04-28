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
class PostGenerationStrategyTest {

    @Mock
    private lateinit var llmOrchestrator: LlmOrchestrator

    @Mock
    private lateinit var ruleRepository: YamlRuleRepository

    @Mock
    private lateinit var scoreCalculator: ComplianceScoreCalculator

    private lateinit var strategy: PostGenerationStrategy

    @BeforeEach
    fun setUp() {
        strategy = PostGenerationStrategy(
            llmOrchestrator = llmOrchestrator,
            ruleRepository = ruleRepository,
            scoreCalculator = scoreCalculator,
            warningThreshold = 70.0
        )
    }

    @Test
    fun `generate succeeds on first iteration when code is valid`() {
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
        assertEquals(StrategyType.POST, response.data?.strategy)
        assertEquals(1, response.data?.iterations)
        assertEquals(100.0, response.data?.score?.total)
        verify(llmOrchestrator, times(1)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `generate retries when code violates rules and succeeds on second iteration`() {
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

        val invalidCode = "public class UserLogic { }"
        val validCode = "public class UserService { }"
        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any()))
            .thenReturn(invalidCode)
            .thenReturn(validCode)

        // Первый скор – низкий, с нарушением (обязательно указать violations!)
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
        val highScore = ComplianceScore(
            total = 100.0,
            rulesPass = 100.0,
            patternMatch = 100.0,
            dependencyCorrect = 100.0,
            violations = emptyList()
        )
        whenever(scoreCalculator.calculate(any(),any(),any(),any(),any()))
            .thenReturn(lowScore)
            .thenReturn(highScore)

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success)
        assertEquals(validCode, response.data?.code)
        assertEquals(StrategyType.POST, response.data?.strategy)
        assertEquals(2, response.data?.iterations)
        assertEquals(100.0, response.data?.score?.total)
        verify(llmOrchestrator, times(2)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `generate returns best result after exhausting iterations`() {
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

        // Низкий скор – с нарушением, чтобы цикл не завершился досрочно
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
        whenever(scoreCalculator.calculate(any(),any(),any(),any(),any()))
            .thenReturn(lowScore)

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success) // успех с предупреждением
        assertEquals(badCode, response.data?.code)
        assertEquals(StrategyType.POST, response.data?.strategy)
        assertEquals(2, response.data?.iterations)
        assertEquals(40.0, response.data?.score?.total)
        assertTrue(response.data?.warnings?.any { it.contains("Could not achieve compliance") } == true)
        verify(llmOrchestrator, times(2)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `generate includes error feedback in user prompt on retry`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            maxIterations = 2,
            expectedClassName = "TestService"
        )

        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = emptyList())
        )

        // FIXED: Используем doAnswer для захвата userPrompt аргументов
        val capturedUserPrompts = mutableListOf<String>()

        doAnswer { invocation ->
            // Индекс 1 — это userPrompt (0 = systemPrompt, 1 = userPrompt, 2 = maxRetries)
            val userPrompt = invocation.getArgument<String>(1)
            capturedUserPrompts.add(userPrompt)
            "code" // Возвращаемое значение
        }.whenever(llmOrchestrator).generateCodeRaw(any(), any(), any())

        val score1 = ComplianceScore(
            total = 50.0,
            rulesPass = 100.0,
            patternMatch = 0.0,
            dependencyCorrect = 100.0,
            violations = listOf(
                Violation(
                    ruleId = "test",
                    description = "Class should end with Service",
                    className = "BadName",
                    severity = Severity.ERROR
                )
            )
        )
        val score2 = ComplianceScore(
            total = 100.0,
            rulesPass = 100.0,
            patternMatch = 100.0,
            dependencyCorrect = 100.0,
            violations = emptyList()
        )

        whenever(
            scoreCalculator.calculate(any(), any(), any(), any(), any())
        ).thenReturn(score1, score2)

        // Act
        strategy.generate(request)

        // Assert: Проверяем захваченные промпты
        assertEquals(2, capturedUserPrompts.size, "Should have 2 prompts (2 iterations)")

        // Первый промпт — без ошибок
        val firstUserPrompt = capturedUserPrompts[0]
        assertTrue(firstUserPrompt.contains("Create service"))

        // Второй промпт — с ошибками из первой итерации
        val secondUserPrompt = capturedUserPrompts[1]
        assertTrue(secondUserPrompt.contains("Class should end with Service"))
        assertTrue(secondUserPrompt.contains("Исправь эти нарушения"))

        // Проверяем, что LLM вызывался ровно 2 раза
        verify(llmOrchestrator, times(2)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `generate handles empty prompt with error`() {
        val response = strategy.generate(
            CodeGenerationRequest(prompt = "", projectId = "test")
        )
        assertFalse(response.success)
        assertEquals("INVALID_PROMPT", response.error?.code)
        verify(llmOrchestrator, never()).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `generate skips validation when class name extraction fails`() {
        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "test",
            collectMetrics = true,
            expectedClassName = null   // не указано явно
        )
        whenever(ruleRepository.load("test")).thenReturn(
            RulesConfig(projectId = "test", rules = emptyList())
        )
        // Код без объявления класса
        val codeWithoutClass = "public void someMethod() { }"
        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any())).thenReturn(codeWithoutClass)

        // Act
        val response = strategy.generate(request)

        // Assert
        assertTrue(response.success)
        // Проверяем, что в warnings добавлено сообщение о пропуске валидации
        assertTrue(response.data?.warnings?.any { it.contains("Could not extract class name") } == true)
        verify(scoreCalculator, never()).calculate(any(), any(), any(), any(), any())
    }

    @Test
    fun `generate respects maxIterations and does not exceed`() {
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

        // Всегда возвращаем низкий скор с violation, чтобы цикл не завершался
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

        val response = strategy.generate(request)

        // Успех, но с предупреждением, и выполнено ровно 3 итерации
        assertTrue(response.success)
        assertEquals(3, response.data?.iterations)
        verify(llmOrchestrator, times(3)).generateCodeRaw(any(), any(), any())
    }

    @Test
    fun `generation and validation times are accumulated across iterations`() {
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

        // Имитируем задержку генерации
        whenever(llmOrchestrator.generateCodeRaw(any(), any(), any()))
            .thenAnswer { Thread.sleep(50); "code1" }
            .thenAnswer { Thread.sleep(50); "code2" }

        // Первый скор – низкий с violation, второй – высокий
        val lowScore = ComplianceScore(
            total = 50.0,
            rulesPass = 100.0,
            patternMatch = 0.0,
            dependencyCorrect = 100.0,
            violations = listOf(Violation("test", "desc", "TestClass", severity = Severity.INFO))
        )
        val highScore = ComplianceScore(
            total = 100.0,
            rulesPass = 100.0,
            patternMatch = 100.0,
            dependencyCorrect = 100.0,
            violations = emptyList()
        )
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any()))
            .thenAnswer { Thread.sleep(30); lowScore }
            .thenAnswer { Thread.sleep(30); highScore }

        val response = strategy.generate(request)

        assertTrue(response.success)
        // Время генерации должно быть не менее 2*50 = 100 мс (с учётом погрешности допускаем 90)
        assertTrue(response.metadata.generationTimeMs >= 90)
        // Время валидации не менее 2*30 = 60 мс (допуск 50)
        assertTrue(response.metadata.validationTimeMs >= 50)
        assertEquals(
            response.metadata.generationTimeMs + response.metadata.validationTimeMs,
            response.metadata.totalTimeMs
        )
    }

    @Test
    fun `generate handles validation exception gracefully`() {
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

        val response = strategy.generate(request)

        assertFalse(response.success)
        assertEquals("VALIDATION_ERROR", response.error?.code)
        assertTrue(response.error?.message?.contains("Compilation failed") == true)
        verify(llmOrchestrator, times(1)).generateCodeRaw(any(), any(), any())
        verify(scoreCalculator, times(1)).calculate(any(), any(), any(), any(), any())
    }
}