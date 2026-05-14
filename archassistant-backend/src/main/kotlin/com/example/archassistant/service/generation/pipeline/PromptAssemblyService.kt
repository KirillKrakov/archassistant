package com.example.archassistant.service.generation.pipeline

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.model.GenerationPrompt
import com.example.archassistant.model.PreparedGenerationRequest
import com.example.archassistant.model.StrategyType
import com.example.archassistant.model.Violation
import com.example.archassistant.util.ErrorFormatter
import com.example.archassistant.util.PromptFormatter
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class PromptAssemblyService {

    fun buildPrompt(
        strategyType: StrategyType,
        request: CodeGenerationRequest,
        prepared: PreparedGenerationRequest,
        previousViolations: List<Violation> = emptyList(),
        previousScore: Double? = null
    ): GenerationPrompt {
        val rulesForPrompt = when (strategyType) {
            StrategyType.PRE -> prepared.rules
            StrategyType.POST -> emptyList()
            StrategyType.HYBRID -> prepared.rules
        }

        val systemPrompt = PromptFormatter.formatSystemPrompt(
            rules = rulesForPrompt,
            languageHint = prepared.languageHint()
        )

        val userPromptBase = PromptFormatter.formatUserPrompt(
            originalRequest = request.prompt,
            previousErrors = previousViolationsForPrompt(previousViolations),
            projectContext = prepared.promptContext(request),
            codeContext = request.context?.codeSnippet
        )

        val retryInstruction = buildRetryInstruction(
            strategyType = strategyType,
            previousViolations = previousViolations,
            previousScore = previousScore
        )

        val userPrompt = if (retryInstruction.isNullOrBlank()) {
            userPromptBase
        } else {
            "$userPromptBase\n\n$retryInstruction"
        }

        return GenerationPrompt(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
    }

    private fun previousViolationsForPrompt(violations: List<Violation>): List<String> {
        return violations.take(5).map { violation ->
            buildString {
                append(violation.description)
                if (violation.className.isNotBlank() && violation.className != "*") {
                    append(" (class: ${violation.className})")
                }
            }
        }
    }

    private fun buildRetryInstruction(
        strategyType: StrategyType,
        previousViolations: List<Violation>,
        previousScore: Double?
    ): String? {
        return when {
            previousViolations.isNotEmpty() -> ErrorFormatter.formatFixInstruction(previousViolations)
            previousScore != null -> {
                val formatted = String.format(Locale.US, "%.2f", previousScore)
                "The previous ${strategyType.name.lowercase()} attempt scored $formatted%. Improve architectural compliance and reduce violations."
            }
            else -> null
        }
    }
}