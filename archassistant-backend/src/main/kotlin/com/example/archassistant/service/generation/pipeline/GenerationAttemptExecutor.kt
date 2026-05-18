package com.example.archassistant.service.generation.pipeline

import com.example.archassistant.model.generation.GenerationAttemptResult
import com.example.archassistant.model.generation.GenerationPrompt
import com.example.archassistant.service.generation.LlmOrchestrator
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class GenerationAttemptExecutor(
    private val llmOrchestrator: LlmOrchestrator
) {

    fun execute(prompt: GenerationPrompt, maxRetries: Int): GenerationAttemptResult {
        val effectiveRetries = maxRetries.coerceAtLeast(1)

        var generatedRawCode: String?
        val generationTimeMs = measureTimeMillis {
            generatedRawCode = llmOrchestrator.generateCodeRaw(
                systemPrompt = prompt.systemPrompt,
                userPrompt = prompt.userPrompt,
                maxRetries = effectiveRetries
            )
        }

        val rawCode = generatedRawCode?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("LLM returned empty response")

        return GenerationAttemptResult(
            rawCode = rawCode,
            generationTimeMs = generationTimeMs
        )
    }

    fun currentModelName(): String? = llmOrchestrator.extractModelName()
}