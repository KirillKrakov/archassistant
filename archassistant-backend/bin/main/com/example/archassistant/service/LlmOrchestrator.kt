package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.dto.GenerationResponseFactory
import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.StrategyType
import com.example.archassistant.util.PromptFormatter
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class LlmOrchestrator(
    private val chatClient: ChatClient,
    private val ruleRepository: YamlRuleRepository,
    private val projectContextService: ProjectContextService
) {

    private val logger = LoggerFactory.getLogger(LlmOrchestrator::class.java)

    fun generateCode(
        prompt: String,
        projectId: String,
        rules: List<ArchitecturalRule>? = null,
        codeContext: String? = null,
        maxRetries: Int = 3
    ): CodeGenerationResponse {

        if (prompt.isBlank()) {
            return GenerationResponseFactory.error(
                errorCode = "INVALID_PROMPT",
                message = "Prompt cannot be empty",
                totalTimeMs = 0
            )
        }

        lateinit var rawResult: CodeGenerationResponse
        val generationTime = measureTimeMillis {
            rawResult = try {
                val effectiveRules = rules
                    ?: ruleRepository.load(projectId)?.getEnabledRules()
                    ?: emptyList()

                val projectContext = projectContextService.getProjectContext(projectId)

                val systemPrompt = PromptFormatter.formatSystemPrompt(effectiveRules)
                val userPrompt = PromptFormatter.formatUserPrompt(
                    originalRequest = prompt,
                    previousErrors = emptyList(),
                    projectContext = projectContext?.promptContext(),
                    codeContext = codeContext
                )

                logger.debug("System prompt (first 200 chars): ${systemPrompt.take(200)}")
                logger.debug("User prompt (first 200 chars): ${userPrompt.take(200)}")

                val generatedCode = callLlmWithRetry(systemPrompt, userPrompt, maxRetries)

                GenerationResponseFactory.success(
                    code = generatedCode,
                    score = null,
                    strategy = StrategyType.PRE,
                    iterations = 1,
                    generationTimeMs = 0,
                    validationTimeMs = 0,
                    model = extractModelName()
                )
            } catch (e: LlmGenerationException) {
                logger.error("LLM generation failed: ${e.message}", e)
                GenerationResponseFactory.error(
                    errorCode = "LLM_ERROR",
                    message = e.message ?: "Failed to generate code",
                    totalTimeMs = 0
                )
            } catch (e: Exception) {
                logger.error("Unexpected error during generation: ${e.message}", e)
                GenerationResponseFactory.error(
                    errorCode = "INTERNAL_ERROR",
                    message = "Internal server error",
                    totalTimeMs = 0
                )
            }
        }

        return if (rawResult.success) {
            rawResult.copy(
                metadata = rawResult.metadata.copy(generationTimeMs = generationTime)
            )
        } else {
            rawResult.copy(
                metadata = rawResult.metadata.copy(totalTimeMs = generationTime)
            )
        }
    }

    private fun callLlmWithRetry(
        systemPrompt: String,
        userPrompt: String,
        maxRetries: Int
    ): String {
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                val chatResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse()

                val usage = chatResponse?.metadata?.usage
                logger.info(
                    "LLM usage: promptTokens={}, generationTokens={}, totalTokens={}",
                    usage?.promptTokens,
                    usage?.generationTokens,
                    usage?.totalTokens
                )

                return chatResponse?.results?.firstOrNull()
                    ?.output
                    ?.content
                    ?: throw LlmGenerationException("Empty response from LLM", isRetryable = false)

            } catch (e: LlmGenerationException) {
                if (!e.isRetryable) throw e
                lastException = e
            } catch (e: Exception) {
                lastException = e
            }

            if (attempt < maxRetries) Thread.sleep(1000L * attempt)
        }

        throw LlmGenerationException(
            "Failed after $maxRetries attempts: ${lastException?.message}",
            isRetryable = false
        )
    }

    fun extractModelName(): String? {
        return System.getenv("SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL")
            ?: System.getProperty("spring.ai.openai.chat.options.model")
    }

    fun generateWithStrategy(
        prompt: String,
        projectId: String,
        strategy: StrategyType,
        rules: List<ArchitecturalRule>? = null,
        codeContext: String? = null
    ): CodeGenerationResponse {
        return generateCode(prompt, projectId, rules, codeContext)
    }

    fun generateCodeRaw(
        systemPrompt: String,
        userPrompt: String,
        maxRetries: Int = 3
    ): String {
        return callLlmWithRetry(systemPrompt, userPrompt, maxRetries)
    }
}

/**
 * Исключение генерации через LLM
 */
class LlmGenerationException(
    message: String,
    val isRetryable: Boolean = true
) : RuntimeException(message)