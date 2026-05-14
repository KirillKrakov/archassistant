package com.example.archassistant.service.generation.strategy

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.model.ComplianceScore
import com.example.archassistant.model.StrategyType
import com.example.archassistant.service.generation.validation.ComplianceScoreCalculator
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import com.example.archassistant.service.context.ProjectContextService
import com.example.archassistant.service.generation.LlmOrchestrator
import com.example.archassistant.util.PromptFormatter
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class PreGenerationStrategy(
    llmOrchestrator: LlmOrchestrator,
    ruleRepository: YamlRuleRepository,
    scoreCalculator: ComplianceScoreCalculator,
    projectContextService: ProjectContextService,
    properties: ArchassistantProperties
) : BaseGenerationStrategy(
    llmOrchestrator,
    ruleRepository,
    scoreCalculator,
    projectContextService,
    properties
), com.example.archassistant.model.CodeGenerationStrategy {

    override val strategyType: StrategyType = StrategyType.PRE

    override fun generate(request: CodeGenerationRequest): CodeGenerationResponse {
        val prepared = try {
            prepare(request)
        } catch (e: IllegalArgumentException) {
            return error("INVALID_PROMPT", e.message ?: "Invalid prompt")
        } catch (e: Exception) {
            return error("PROJECT_CONTEXT_NOT_AVAILABLE", e.message ?: "Project context is unavailable")
        }

        val systemPrompt = PromptFormatter.formatSystemPrompt(
            rules = prepared.rules,
            languageHint = prepared.projectContext.preferredLanguageHint()
        )

        val userPrompt = PromptFormatter.formatUserPrompt(
            originalRequest = request.prompt,
            previousErrors = emptyList(),
            projectContext = prepared.projectContext.promptContext(
                requestText = request.prompt,
                targetPackage = prepared.normalizedTargetPackage,
                expectedClassName = prepared.normalizedExpectedClassName,
                existingTypes = prepared.normalizedExistingTypes
            ),
            codeContext = request.context?.codeSnippet
        )

        var generationTime = 0L
        val rawCode = try {
            var generatedRawCode: String? = null
            generationTime = measureTimeMillis {
                generatedRawCode = llmOrchestrator.generateCodeRaw(
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    maxRetries = request.maxIterations
                )
            }
            generatedRawCode ?: throw IllegalStateException("LLM returned empty response")
        } catch (e: Exception) {
            return error("LLM_ERROR", e.message ?: "Failed to generate code")
        }

        val generatedCode = cleanAndNormalizeCode(
            rawCode = rawCode,
            projectContext = prepared.projectContext,
            primaryTypeName = extractPrimaryTypeName(request, rawCode, rawCode)
        )

        val warnings = mutableListOf<String>()
        var score: ComplianceScore? = null
        var validationTimeMs = 0L

        if (request.collectMetrics) {
            val validation = validateGeneratedCode(
                generatedCode = generatedCode,
                className = extractPrimaryTypeName(request, generatedCode, rawCode),
                rules = prepared.rules,
                classpath = request.classpath.orEmpty(),
                projectContext = prepared.projectContext
            )
            score = validation.score
            validationTimeMs = validation.validationTimeMs

            if (score != null && score.total < warningThreshold) {
                warnings += "Compliance Score ${score.total}% is below threshold $warningThreshold%."
            }
        }

        if (prepared.rules.isNotEmpty()) {
            warnings += "Pre-Strategy injects rules into the prompt but does not strictly enforce them."
        }

        return success(
            code = generatedCode,
            score = score,
            strategy = StrategyType.PRE,
            iterations = 1,
            generationTimeMs = generationTime,
            validationTimeMs = validationTimeMs,
            warnings = warnings
        )
    }
}