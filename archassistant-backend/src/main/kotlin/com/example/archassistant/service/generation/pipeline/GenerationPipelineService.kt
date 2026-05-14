package com.example.archassistant.service.generation.pipeline

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.model.GenerationValidationResult
import com.example.archassistant.model.PreparedGenerationRequest
import com.example.archassistant.model.StrategyType
import com.example.archassistant.model.Violation
import org.springframework.stereotype.Service

@Service
class GenerationPipelineService(
    private val preparationService: GenerationPreparationService,
    private val promptAssemblyService: PromptAssemblyService,
    private val attemptExecutor: GenerationAttemptExecutor,
    private val validationFacade: ValidationFacade,
    private val responseAssembler: GenerationResponseAssembler,
    private val strategyPolicy: StrategyIterationPolicy,
    private val properties: ArchassistantProperties
) {

    private companion object {
        const val PROVIDER_RETRY_ATTEMPTS = 2
    }

    fun generate(request: CodeGenerationRequest, strategyType: StrategyType): CodeGenerationResponse {
        val prepared = try {
            preparationService.prepare(request)
        } catch (e: IllegalArgumentException) {
            return responseAssembler.error("INVALID_PROMPT", e.message ?: "Invalid prompt")
        } catch (e: Exception) {
            return responseAssembler.error("PROJECT_CONTEXT_NOT_AVAILABLE", e.message ?: "Project context is unavailable")
        }

        return if (strategyPolicy.shouldRetry(strategyType)) {
            generateWithRetries(request, prepared, strategyType)
        } else {
            generatePre(request, prepared, strategyType)
        }
    }

    private fun generatePre(
        request: CodeGenerationRequest,
        prepared: PreparedGenerationRequest,
        strategyType: StrategyType
    ): CodeGenerationResponse {
        val prompt = promptAssemblyService.buildPrompt(
            strategyType = strategyType,
            request = request,
            prepared = prepared
        )

        val attempt = try {
            attemptExecutor.execute(
                prompt = prompt,
                maxRetries = maxOf(1, request.maxIterations)
            )
        } catch (e: Exception) {
            return responseAssembler.error("LLM_ERROR", e.message ?: "Failed to generate code")
        }

        val validation = validationFacade.analyze(
            request = request,
            prepared = prepared,
            rawCode = attempt.rawCode,
            performValidation = request.collectMetrics
        )

        val warnings = mutableListOf<String>()

        if (validation.score != null && validation.score.total < properties.compliance.threshold) {
            warnings += strategyPolicy.lowScoreWarning(validation.score.total)
        }

        if (prepared.rules.isNotEmpty()) {
            warnings += "Pre-Strategy injects rules into the prompt but does not strictly enforce them."
        }

        return responseAssembler.success(
            code = validation.generatedCode,
            score = validation.score,
            strategy = strategyType,
            iterations = 1,
            generationTimeMs = attempt.generationTimeMs,
            validationTimeMs = validation.validationTimeMs,
            modelName = attemptExecutor.currentModelName(),
            warnings = warnings
        )
    }

    private fun generateWithRetries(
        request: CodeGenerationRequest,
        prepared: PreparedGenerationRequest,
        strategyType: StrategyType
    ): CodeGenerationResponse {
        val maxIterations = maxOf(1, request.maxIterations)

        var iteration = 0
        var lastRawCode = ""
        var lastValidation: GenerationValidationResult? = null
        var totalGenerationTime = 0L
        var totalValidationTime = 0L
        var previousViolations: List<Violation> = emptyList()
        val warnings = mutableListOf<String>()

        while (iteration < maxIterations) {
            iteration++

            val prompt = promptAssemblyService.buildPrompt(
                strategyType = strategyType,
                request = request,
                prepared = prepared,
                previousViolations = previousViolations,
                previousScore = lastValidation?.score?.total
            )

            val attempt = try {
                attemptExecutor.execute(
                    prompt = prompt,
                    maxRetries = PROVIDER_RETRY_ATTEMPTS
                )
            } catch (e: Exception) {
                return responseAssembler.error(
                    errorCode = "LLM_ERROR",
                    message = e.message ?: "Failed at iteration $iteration",
                    totalTimeMs = totalGenerationTime + totalValidationTime
                )
            }

            totalGenerationTime += attempt.generationTimeMs
            lastRawCode = attempt.rawCode

            val validation = validationFacade.analyze(
                request = request,
                prepared = prepared,
                rawCode = attempt.rawCode,
                performValidation = true
            )

            lastValidation = validation
            totalValidationTime += validation.validationTimeMs

            if (strategyPolicy.isSuccessful(validation)) {
                if (iteration > 1) {
                    warnings += strategyPolicy.retryWarning(strategyType, iteration, maxIterations)
                }

                return responseAssembler.success(
                    code = validation.generatedCode,
                    score = validation.score,
                    strategy = strategyType,
                    iterations = iteration,
                    generationTimeMs = totalGenerationTime,
                    validationTimeMs = totalValidationTime,
                    modelName = attemptExecutor.currentModelName(),
                    warnings = warnings
                )
            }

            previousViolations = validation.violations
        }

        lastValidation?.score?.let {
            if (it.total < properties.compliance.threshold) {
                warnings += strategyPolicy.lowScoreWarning(it.total)
            }
        }

        warnings += strategyPolicy.exhaustionWarning(strategyType, maxIterations)

        return responseAssembler.success(
            code = lastValidation?.generatedCode ?: lastRawCode,
            score = lastValidation?.score,
            strategy = strategyType,
            iterations = maxIterations,
            generationTimeMs = totalGenerationTime,
            validationTimeMs = totalValidationTime,
            modelName = attemptExecutor.currentModelName(),
            warnings = warnings
        )
    }
}