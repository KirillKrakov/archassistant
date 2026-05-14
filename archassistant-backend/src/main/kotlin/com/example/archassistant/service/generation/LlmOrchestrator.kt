package com.example.archassistant.service.generation

import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.dto.generation.response.GenerationResponseFactory
import com.example.archassistant.service.generation.client.LlmClientFactory
import com.example.archassistant.util.PromptFormatter
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis
import com.example.archassistant.model.rules.ArchitecturalRule

@Service
class LlmOrchestrator(
    private val chatClient: ChatClient,
    private val llmClientFactory: LlmClientFactory
) {

    private val logger = LoggerFactory.getLogger(LlmOrchestrator::class.java)

    fun generateCodeRaw(
        systemPrompt: String,
        userPrompt: String,
        maxRetries: Int = 3
    ): String {
        return callLlmWithRetry(systemPrompt, userPrompt, maxRetries)
    }

    fun extractModelName(): String? = llmClientFactory.currentModelName()

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

        var generationTime = 0L

        val systemPrompt = PromptFormatter.formatSystemPrompt(rules.orEmpty())
        val userPrompt = PromptFormatter.formatUserPrompt(
            originalRequest = prompt,
            previousErrors = emptyList(),
            projectContext = null,
            codeContext = codeContext
        )

        return try {
            val code = run {
                var generatedCode: String?
                generationTime = measureTimeMillis {
                    generatedCode = callLlmWithRetry(systemPrompt, userPrompt, maxRetries)
                }
                generatedCode ?: throw LlmGenerationException(
                    "Empty response from LLM",
                    isRetryable = false
                )
            }

            GenerationResponseFactory.success(
                code = code,
                score = null,
                strategy = com.example.archassistant.model.StrategyType.PRE,
                iterations = 1,
                generationTimeMs = generationTime,
                validationTimeMs = 0,
                model = extractModelName()
            )
        } catch (e: LlmGenerationException) {
            GenerationResponseFactory.error(
                errorCode = "LLM_ERROR",
                message = e.message ?: "Failed to generate code",
                totalTimeMs = generationTime
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
                    usage?.completionTokens,
                    usage?.totalTokens
                )

                return chatResponse?.results?.firstOrNull()
                    ?.output
                    ?.text
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
}