import com.example.archassistant.model.*
import com.example.archassistant.service.LlmGenerationException
import com.example.archassistant.service.LlmOrchestrator
import com.example.archassistant.service.YamlRuleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class LlmOrchestratorTest {

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

    private lateinit var orchestrator: LlmOrchestrator

    @BeforeEach
    fun setUp() {
        // Создаем реальный объект репозитория
        val ruleRepository = YamlRuleRepository(ObjectMapper(), ".test-config")
        orchestrator = LlmOrchestrator(chatClient, ruleRepository)
    }

    @Test
    fun `generateCode calls LLM with formatted prompts`() {
        val expectedCode = "public class UserService { }"

        val sampleRule = ArchitecturalRule(
            id = "test_rule",
            name = "Test rule",
            type = RuleType.DEPENDENCY,
            fromPackage = "..*",
            toPackage = "..*",
            constraint = ConstraintType.NO_DEPENDENCY
        )

        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(systemPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.user(userPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn(expectedCode)

        // Передаём правила явно, чтобы не зависеть от мока репозитория
        val response = orchestrator.generateCode(
            prompt = "Create UserService",
            projectId = "test-project",
            rules = listOf(sampleRule)   // ← добавили правила
        )

        assertTrue(response.success)
        assertEquals(expectedCode, response.data?.code)

        val systemPrompt = systemPromptCaptor.value
        assertTrue(systemPrompt.contains("АРХИТЕКТУРНЫЕ ПРАВИЛА"))
        assertTrue(systemPrompt.contains("Возвращай ТОЛЬКО код"))

        val userPrompt = userPromptCaptor.value
        assertTrue(userPrompt.contains("Create UserService"))
    }

    @Test
    fun `generateCode includes rules in system prompt`() {
        val rules = listOf(
            ArchitecturalRule(
                id = "rule_001",
                name = "Services should not depend on controllers",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY,
                severity = Severity.CRITICAL
            )
        )

        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(systemPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn("code")

        orchestrator.generateCode(
            prompt = "Test",
            projectId = "test",
            rules = rules
        )

        val systemPrompt = systemPromptCaptor.value
        assertTrue(systemPrompt.contains("Services should not depend on controllers"))
        assertTrue(systemPrompt.contains("[ОБЯЗАТЕЛЬНО]"))
    }

    @Test
    fun `generateCode handles LLM error with retry`() {
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call())
            .thenThrow(RuntimeException("Temporary error"))
            .thenThrow(RuntimeException("Temporary error"))
            .thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn("recovered code")

        val response = orchestrator.generateCode(
            prompt = "Test",
            projectId = "test",
            maxRetries = 3
        )

        assertTrue(response.success)
        assertEquals("recovered code", response.data?.code)
    }

    @Test
    fun `generateCode returns error after max retries`() {
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenThrow(RuntimeException("Persistent error"))

        val response = orchestrator.generateCode(
            prompt = "Test",
            projectId = "test",
            maxRetries = 2
        )

        assertFalse(response.success)
        assertEquals("LLM_ERROR", response.error?.code)
    }

    @Test
    fun `generateCode does not retry on non-retryable error`() {
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenThrow(
            LlmGenerationException("Configuration error", isRetryable = false)
        )

        val response = orchestrator.generateCode(
            prompt = "Test",
            projectId = "test",
            maxRetries = 3
        )

        assertFalse(response.success)
        verify(requestSpec, times(1)).call()
    }

    @Test
    fun `generation time is recorded`() {
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn("public class Test {}")

        val response = orchestrator.generateCode(prompt = "Test", projectId = "test")

        assertTrue(response.success)
        assertTrue(response.metadata.generationTimeMs > 0, "Generation time should be positive")
        assertTrue(response.metadata.generationTimeMs < 10000, "Generation should complete within 10 seconds")
    }
}