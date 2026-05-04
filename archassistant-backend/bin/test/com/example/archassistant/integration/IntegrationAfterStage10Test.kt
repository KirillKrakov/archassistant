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
import java.nio.file.Path

@SpringBootTest
@Import(YamlConfig::class)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension::class)
class IntegrationAfterStage10Test {

    @TempDir
    lateinit var tempDir: Path

    @Autowired
    private lateinit var yamlMapper: ObjectMapper

    @Mock
    private lateinit var chatClient: ChatClient

    @Mock
    private lateinit var scoreCalculator: ComplianceScoreCalculator

    @Mock
    private lateinit var requestSpec: ChatClient.ChatClientRequestSpec

    @Mock
    private lateinit var responseSpec: ChatClient.CallResponseSpec

    @Captor
    private lateinit var userPromptCaptor: ArgumentCaptor<String>

    private lateinit var ruleRepository: YamlRuleRepository
    private lateinit var compiler: CodeCompiler
    private lateinit var orchestrator: LlmOrchestrator
    private lateinit var postStrategy: PostGenerationStrategy

    @BeforeEach
    fun setUp() {
        ruleRepository = YamlRuleRepository(yamlMapper, tempDir.toString())
        compiler = CodeCompiler()
        orchestrator = LlmOrchestrator(chatClient, ruleRepository)
        postStrategy = PostGenerationStrategy(
            llmOrchestrator = orchestrator,
            ruleRepository = ruleRepository,
            scoreCalculator = scoreCalculator,
            warningThreshold = 70.0
        )
    }

    /**
     * Хелпер для мокирования последовательных ответов LLM.
     * Предполагается, что вызовы chatClient.prompt() происходят в том же порядке,
     * что и переданные responses.
     */
    private fun mockLlmResponse(vararg responses: String) {
        var callCount = 0
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        // Захватываем user prompt для проверки обратной связи
        whenever(requestSpec.user(userPromptCaptor.capture())).thenReturn(requestSpec)
        doAnswer {
            val idx = callCount++
            if (idx < responses.size) responseSpec
            else throw RuntimeException("No more mocked responses")
        }.whenever(requestSpec).call()
        whenever(responseSpec.content()).thenAnswer { responses[callCount - 1] }
    }

    // region Тесты Этапа 8 с реальным калькулятором
    @Test
    fun `stage8 compliance score - excellent for valid code`() {
        val realScoreCalculator = ComplianceScoreCalculator(compiler)
        val rules = listOf(
            ArchitecturalRule(
                id = "suffix", name = "Service suffix", type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..", constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service", enabled = true
            )
        )
        val code = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            @Service public class UserService { }
        """.trimIndent()
        val score = realScoreCalculator.calculate(code, "UserService", rules)
        assertEquals(100.0, score.total)
        assertEquals(ScoreGrade.EXCELLENT, ScoreGrade.fromScore(score.total))
    }

    @Test
    fun `stage8 compliance score - reduced for naming violation`() {
        val realScoreCalculator = ComplianceScoreCalculator(compiler)
        val rules = listOf(
            ArchitecturalRule(
                id = "suffix", name = "Service suffix", type = RuleType.NAMING_CONVENTION,
                fromPackage = "..service..", constraint = ConstraintType.NAMING_SUFFIX,
                pattern = "Service", enabled = true
            )
        )
        val code = "package com.example.service; public class UserLogic { }"
        val score = realScoreCalculator.calculate(code, "UserLogic", rules)
        assertTrue(score.patternMatch < 100.0)
        assertTrue(score.violations.isNotEmpty())
    }
    // endregion

    // region Тесты Post-Strategy
    @Test
    fun `post-strategy succeeds on first iteration for valid code`() {
        val config = RulesConfig(
            projectId = "post.valid",
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix", name = "Service suffix", type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..", constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service", enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val validCode = "public class UserService { }"
        mockLlmResponse(validCode)
        val perfectScore = ComplianceScore(100.0, 100.0, 100.0, 100.0)

        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any())).thenReturn(perfectScore)

        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "post.valid",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = "UserService"
        )

        val response = postStrategy.generate(request)

        assertTrue(response.success)
        assertEquals(validCode, response.data?.code)
        assertEquals(1, response.data?.iterations)
        assertEquals(100.0, response.data?.score?.total)
        assertTrue(response.data?.warnings?.isEmpty() ?: false)
    }

    @Test
    fun `post-strategy stops when score meets threshold on first iteration`() {
        val config = RulesConfig(projectId = "post.threshold", rules = emptyList())
        ruleRepository.save(config)

        val code = "public class Test { }"
        mockLlmResponse(code)

        // Точное попадание в порог (70.0)
        val thresholdScore = ComplianceScore(70.0, 100.0, 70.0, 100.0)
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any())).thenReturn(thresholdScore)

        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "post.threshold",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = "Test"
        )

        val response = postStrategy.generate(request)

        assertTrue(response.success)
        assertEquals(1, response.data?.iterations)
        assertEquals(70.0, response.data?.score?.total)
        // Нет предупреждения о низком скоре, т.к. порог достигнут
        val lowScoreWarning = response.data?.warnings?.any { it.contains("below threshold") }
        assertFalse(lowScoreWarning ?: false)
    }

    @Test
    fun `post-strategy continues when score is below threshold`() {
        val config = RulesConfig(projectId = "post.below", rules = emptyList())
        ruleRepository.save(config)

        val code = "public class Test { }"
        mockLlmResponse(code, code)

        val belowScore = ComplianceScore(69.9, 100.0, 69.9, 100.0)
        val perfectScore = ComplianceScore(100.0, 100.0, 100.0, 100.0)
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any()))
            .thenReturn(belowScore, perfectScore)

        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "post.below",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = "Test"
        )

        val response = postStrategy.generate(request)

        assertTrue(response.success)
        // Должно быть 2 итерации: первая с 69.9 (<70), вторая успешная
        assertEquals(2, response.data?.iterations)
        assertEquals(100.0, response.data?.score?.total)
    }

    @Test
    fun `post-strategy retries and succeeds on second iteration with error feedback`() {
        val config = RulesConfig(
            projectId = "post.retry",
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix", name = "Service suffix", type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..", constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service", enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val invalidCode = "public class UserLogic { }"
        val validCode = "public class UserService { }"
        mockLlmResponse(invalidCode, validCode)

        val lowScore = ComplianceScore(
            total = 50.0, rulesPass = 100.0, patternMatch = 0.0, dependencyCorrect = 100.0,
            violations = listOf(Violation("suffix", "should end with Service", "UserLogic", severity = Severity.INFO))
        )
        val highScore = ComplianceScore(100.0, 100.0, 100.0, 100.0)

        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any()))
            .thenReturn(lowScore, highScore)

        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "post.retry",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = "UserService"
        )

        val response = postStrategy.generate(request)

        assertTrue(response.success)
        assertEquals(validCode, response.data?.code)
        assertEquals(2, response.data?.iterations)
        val warning = response.data?.warnings?.firstOrNull { it.contains("required 2/3 iterations") }
        assertNotNull(warning)
    }

    @Test
    fun `post-strategy includes error feedback in user prompt on retry`() {
        val config = RulesConfig(projectId = "post.feedback", rules = emptyList())
        ruleRepository.save(config)

        val invalidCode = "public class Invalid { }"
        val validCode = "public class Valid { }"
        mockLlmResponse(invalidCode, validCode)

        val lowScore = ComplianceScore(
            total = 30.0, rulesPass = 100.0, patternMatch = 0.0, dependencyCorrect = 100.0,
            violations = listOf(Violation("test", "Some error", "Invalid", severity = Severity.ERROR))
        )
        val highScore = ComplianceScore(100.0, 100.0, 100.0, 100.0)
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any()))
            .thenReturn(lowScore, highScore)

        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "post.feedback",
            maxIterations = 2,
            collectMetrics = true,
            expectedClassName = "Valid"
        )

        val response = postStrategy.generate(request)

        assertTrue(response.success)
        assertEquals(validCode, response.data?.code)
        assertEquals(2, response.data?.iterations)

        // Проверяем, что во втором user prompt появилась ошибка
        val allUserPrompts = userPromptCaptor.allValues
        // Первый промпт – без ошибок, второй – с ошибками
        val secondPrompt = allUserPrompts.getOrNull(1)
        assertNotNull(secondPrompt)
        assertTrue(secondPrompt!!.contains("Some error"))
        assertTrue(secondPrompt.contains("Исправь эти нарушения"))
    }

    @Test
    fun `post-strategy returns best result after exhausting iterations`() {
        val config = RulesConfig(
            projectId = "post.exhaust",
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix", name = "Service suffix", type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..", constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service", enabled = true
                )
            )
        )
        ruleRepository.save(config)

        val alwaysInvalid = "public class UserLogic { }"
        mockLlmResponse(alwaysInvalid, alwaysInvalid)

        val lowScore = ComplianceScore(
            total = 40.0, rulesPass = 100.0, patternMatch = 0.0, dependencyCorrect = 100.0,
            violations = listOf(Violation("suffix", "should end with Service", "UserLogic", severity = Severity.INFO))
        )
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any())).thenReturn(lowScore)

        val request = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = "post.exhaust",
            maxIterations = 2,
            collectMetrics = true,
            expectedClassName = "UserService"
        )

        val response = postStrategy.generate(request)

        assertTrue(response.success)
        assertEquals(2, response.data?.iterations)
        val warning = response.data?.warnings?.firstOrNull { it.contains("Could not achieve compliance") }
        assertNotNull(warning)
        assertEquals(alwaysInvalid, response.data?.code)
    }

    @Test
    fun `post-strategy handles validation exception gracefully`() {
        val config = RulesConfig(projectId = "post.exception", rules = emptyList())
        ruleRepository.save(config)

        mockLlmResponse("code")
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("Compilation failed"))

        val request = CodeGenerationRequest(
            prompt = "Create service",
            projectId = "post.exception",
            maxIterations = 2,
            collectMetrics = true,
            expectedClassName = "Test"
        )

        val response = postStrategy.generate(request)

        assertFalse(response.success)
        assertEquals("VALIDATION_ERROR", response.error?.code)
        assertTrue(response.error?.message?.contains("Compilation failed") == true)
    }

    @Test
    fun `post-strategy accumulates generation and validation times across iterations`() {
        val config = RulesConfig(projectId = "post.timing", rules = emptyList())
        ruleRepository.save(config)

        // Настройка мока генерации с задержкой
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        doAnswer { Thread.sleep(50); responseSpec }.whenever(requestSpec).call()
        whenever(responseSpec.content()).thenReturn("code")

        // Низкий скор с нарушением, высокий скор — оба с задержкой для измерения времени валидации
        val lowScore = ComplianceScore(
            total = 50.0, rulesPass = 100.0, patternMatch = 0.0, dependencyCorrect = 100.0,
            violations = listOf(Violation("test", "desc", "Test", severity = Severity.INFO))
        )
        val highScore = ComplianceScore(100.0, 100.0, 100.0, 100.0)

        // Используем doAnswer для имитации задержки валидации (20 мс на каждый вызов)
        doAnswer { Thread.sleep(20); lowScore }
            .doAnswer { Thread.sleep(20); highScore }
            .whenever(scoreCalculator).calculate(any(), any(), any(), any(), any())

        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "post.timing",
            maxIterations = 2,
            collectMetrics = true,
            expectedClassName = "Test"
        )

        val response = postStrategy.generate(request)

        assertTrue(response.success)
        // Генерация: 2 итерации * 50 мс ≈ 100 мс (с допуском)
        assertTrue(response.metadata.generationTimeMs >= 90)
        // Валидация: 2 итерации * 20 мс ≈ 40 мс
        assertTrue(response.metadata.validationTimeMs >= 30)
        assertEquals(
            response.metadata.generationTimeMs + response.metadata.validationTimeMs,
            response.metadata.totalTimeMs
        )
    }

    @Test
    fun `post-strategy respects maxIterations and does not exceed`() {
        val config = RulesConfig(projectId = "post.maxiter", rules = emptyList())
        ruleRepository.save(config)

        mockLlmResponse("code", "code", "code")
        val lowScore = ComplianceScore(
            total = 30.0, rulesPass = 100.0, patternMatch = 0.0, dependencyCorrect = 100.0,
            violations = listOf(Violation("test", "desc", "Test", severity = Severity.ERROR))
        )
        whenever(scoreCalculator.calculate(any(), any(), any(), any(), any())).thenReturn(lowScore)

        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "post.maxiter",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = "Test"
        )

        val response = postStrategy.generate(request)

        assertEquals(3, response.data?.iterations)
        verify(requestSpec, times(3)).call()
    }

    @Test
    fun `post-strategy skips validation when class name extraction fails`() {
        val config = RulesConfig(projectId = "post.skip", rules = emptyList())
        ruleRepository.save(config)

        val codeWithoutClass = "public void method() { }"
        // Три ответа, потому что maxIterations = 3
        mockLlmResponse(codeWithoutClass, codeWithoutClass, codeWithoutClass)

        val request = CodeGenerationRequest(
            prompt = "Create method",
            projectId = "post.skip",
            maxIterations = 3,
            collectMetrics = true,
            expectedClassName = null
        )

        val response = postStrategy.generate(request)

        assertTrue(response.success)
        assertNull(response.data?.score)
        val warning = response.data?.warnings?.firstOrNull { it.contains("Could not extract class name") }
        assertNotNull(warning)
        verify(scoreCalculator, never()).calculate(any(), any(), any(), any(), any())
    }

    @Test
    fun `post-strategy handles empty prompt with error`() {
        val response = postStrategy.generate(CodeGenerationRequest(prompt = "", projectId = "any"))
        assertFalse(response.success)
        assertEquals("INVALID_PROMPT", response.error?.code)
    }
    // endregion
}