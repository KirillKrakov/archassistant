package com.example.archassistant.service.generation.strategy

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.model.ComplianceScore
import com.example.archassistant.model.StrategyType
import com.example.archassistant.model.Violation
import com.example.archassistant.service.validation.ComplianceScoreCalculator
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import com.example.archassistant.service.context.ProjectContextService
import com.example.archassistant.service.generation.LlmOrchestrator
import com.example.archassistant.util.ErrorFormatter
import com.example.archassistant.util.PromptFormatter
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class PostGenerationStrategy(
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

    override val strategyType: StrategyType = StrategyType.POST

    override fun generate(request: CodeGenerationRequest): CodeGenerationResponse {
        val prepared = try {
            prepare(request)
        } catch (e: IllegalArgumentException) {
            return error("INVALID_PROMPT", e.message ?: "Invalid prompt")
        } catch (e: Exception) {
            return error("PROJECT_CONTEXT_NOT_AVAILABLE", e.message ?: "Project context is unavailable")
        }

        val baseSystemPrompt = PromptFormatter.formatSystemPrompt(
            rules = emptyList(),
            languageHint = prepared.projectContext.preferredLanguageHint()
        )

        val baseUserPrompt = PromptFormatter.formatUserPrompt(
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

        var iteration = 0
        var lastCode: String? = null
        var lastScore: ComplianceScore? = null
        var lastViolations: List<Violation> = emptyList()
        var totalGenerationTime = 0L
        var totalValidationTime = 0L
        val warnings = mutableListOf<String>()

        while (iteration < request.maxIterations) {
            iteration++

            val (systemPrompt, userPrompt) = if (iteration == 1) {
                baseSystemPrompt to baseUserPrompt
            } else {
                val fixInstruction = ErrorFormatter.formatFixInstruction(lastViolations)
                val enhancedUserPrompt = PromptFormatter.formatUserPrompt(
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
                baseSystemPrompt to "$enhancedUserPrompt\n\n$fixInstruction"
            }

            val generationTime = measureTimeMillis {
                lastCode = try {
                    llmOrchestrator.generateCodeRaw(
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxRetries = 2
                    )
                } catch (e: Exception) {
                    null
                }
            }

            if (lastCode == null) {
                return error("LLM_ERROR", "Failed at iteration $iteration")
            }

            totalGenerationTime += generationTime

            val generatedCode = cleanAndNormalizeCode(
                rawCode = lastCode!!,
                projectContext = prepared.projectContext,
                primaryTypeName = extractPrimaryTypeName(request, lastCode!!, lastCode!!)
            )

            val validation = validateGeneratedCode(
                generatedCode = generatedCode,
                className = extractPrimaryTypeName(request, generatedCode, lastCode!!),
                rules = prepared.rules,
                classpath = request.classpath.orEmpty(),
                projectContext = prepared.projectContext
            )

            lastScore = validation.score
            lastViolations = validation.violations
            totalValidationTime += validation.validationTimeMs

            if (lastViolations.isEmpty() && (lastScore?.total ?: 0.0) >= warningThreshold) {
                if (iteration > 1) {
                    warnings += "Post-Strategy required $iteration/${request.maxIterations} iterations."
                }
                return success(
                    code = generatedCode,
                    score = lastScore,
                    strategy = StrategyType.POST,
                    iterations = iteration,
                    generationTimeMs = totalGenerationTime,
                    validationTimeMs = totalValidationTime,
                    warnings = warnings
                )
            }
        }

        if (lastScore != null && lastScore.total < warningThreshold) {
            warnings += "Compliance Score ${lastScore.total}% is below threshold $warningThreshold%."
        }

        warnings += "Post-Strategy exhausted ${request.maxIterations} iterations. Manual review is recommended."

        return success(
            code = lastCode.orEmpty(),
            score = lastScore,
            strategy = StrategyType.POST,
            iterations = request.maxIterations,
            generationTimeMs = totalGenerationTime,
            validationTimeMs = totalValidationTime,
            warnings = warnings
        )
    }
}