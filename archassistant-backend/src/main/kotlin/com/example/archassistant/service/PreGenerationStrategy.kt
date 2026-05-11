package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.dto.GenerationResponseFactory
import com.example.archassistant.model.*
import com.example.archassistant.util.CodeCleaner
import com.example.archassistant.util.GeneratedTypeNameExtractor
import com.example.archassistant.util.ProjectImportNormalizer
import com.example.archassistant.util.PromptFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class PreGenerationStrategy(
    private val llmOrchestrator: LlmOrchestrator,
    private val ruleRepository: YamlRuleRepository,
    private val scoreCalculator: ComplianceScoreCalculator,
    private val projectContextService: ProjectContextService,
    private val warningThreshold: Double = 70.0
) : CodeGenerationStrategy {

    private val logger = LoggerFactory.getLogger(PreGenerationStrategy::class.java)
    override val strategyType: StrategyType = StrategyType.PRE

    override fun generate(request: CodeGenerationRequest): CodeGenerationResponse {
        if (request.prompt.isBlank()) {
            return GenerationResponseFactory.error(
                errorCode = "INVALID_PROMPT",
                message = "Prompt cannot be empty",
                totalTimeMs = 0
            )
        }

        val projectContext = try {
            projectContextService.requireProjectContext(request.projectId)
        } catch (e: Exception) {
            return GenerationResponseFactory.error(
                errorCode = "PROJECT_CONTEXT_NOT_AVAILABLE",
                message = e.message ?: "Project context is unavailable",
                totalTimeMs = 0
            )
        }

        val rules = request.rules
            ?.let { ruleIds ->
                ruleRepository.load(request.projectId)?.rules?.filter { it.id in ruleIds && it.enabled }
            }
            ?: ruleRepository.load(request.projectId)?.getEnabledRules()
            ?: emptyList()

        val normalizedTargetPackage = request.normalizedTargetPackage()
        val normalizedExpectedClassName = request.normalizedExpectedClassName()
        val normalizedExistingTypes = request.normalizedExistingTypes()

        val systemPrompt = PromptFormatter.formatSystemPrompt(
            rules = rules,
            languageHint = projectContext.preferredLanguageHint()
        )

        val userPrompt = PromptFormatter.formatUserPrompt(
            originalRequest = request.prompt,
            previousErrors = emptyList(),
            projectContext = projectContext.promptContext(
                requestText = request.prompt,
                targetPackage = normalizedTargetPackage,
                expectedClassName = normalizedExpectedClassName,
                existingTypes = normalizedExistingTypes
            ),
            codeContext = request.context?.codeSnippet
        )

        logger.debug("Pre-Strategy: System prompt (first 200 chars): ${systemPrompt.take(200)}")
        logger.debug("Pre-Strategy: User prompt (first 200 chars): ${userPrompt.take(200)}")
        logger.debug("Pre-Strategy: {} rules injected", rules.size)

        var result: CodeGenerationResponse
        val generationTime = measureTimeMillis {
            result = try {
                val generatedCode = llmOrchestrator.generateCodeRaw(
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    maxRetries = request.maxIterations
                )
                GenerationResponseFactory.success(
                    code = generatedCode,
                    score = null,
                    strategy = StrategyType.PRE,
                    iterations = 1,
                    generationTimeMs = 0,
                    validationTimeMs = 0,
                    model = llmOrchestrator.extractModelName(),
                    warnings = emptyList()
                )
            } catch (e: Exception) {
                GenerationResponseFactory.error(
                    errorCode = "INTERNAL_ERROR",
                    message = e.message ?: "Unexpected error during generation",
                    totalTimeMs = 0
                )
            }
        }

        if (!result.success) {
            return result.copy(metadata = result.metadata.copy(totalTimeMs = generationTime))
        }

        val rawCode = result.data!!.code
        val generatedCode = ProjectImportNormalizer.normalize(
            code = CodeCleaner.cleanCode(rawCode),
            projectContext = projectContext,
            primaryTypeName = normalizedExpectedClassName ?: GeneratedTypeNameExtractor.extract(rawCode)?.replace('$', '.')
        )

        var validationTime: Long = 0
        var score: ComplianceScore? = null
        val warnings = mutableListOf<String>()

        if (request.collectMetrics) {
            val className = normalizedExpectedClassName ?: GeneratedTypeNameExtractor.extract(generatedCode)?.replace('$', '.')

            if (className != null) {
                validationTime = measureTimeMillis {
                    score = scoreCalculator.calculate(
                        code = generatedCode,
                        className = className,
                        rules = rules,
                        classpath = request.classpath ?: "",
                        projectContext = projectContext
                    )
                }

                val currentScore = score
                if (currentScore != null && currentScore.total < warningThreshold) {
                    warnings.add(
                        "Compliance Score ${currentScore.total}% is below threshold $warningThreshold%. " +
                                "Consider using Post/Hybrid strategy for strict validation."
                    )
                }
            } else {
                warnings.add("Could not extract class name from generated code; validation skipped.")
            }
        }

        if (rules.isNotEmpty()) {
            warnings.add(
                "Pre-Strategy: rules are added to prompt but compliance is not enforced. " +
                        "Use Post/Hybrid for strict validation."
            )
        }

        logger.info(
            "Pre-Strategy completed: generation=${generationTime}ms, validation=${validationTime}ms, " +
                    "score=${score?.total ?: "N/A"}%"
        )

        return GenerationResponseFactory.success(
            code = generatedCode,
            score = score,
            strategy = StrategyType.PRE,
            iterations = 1,
            generationTimeMs = generationTime,
            validationTimeMs = validationTime,
            model = llmOrchestrator.extractModelName(),
            warnings = warnings
        )
    }

    private fun CodeGenerationRequest.normalizedTargetPackage(): String? =
        context?.targetPackage
            ?.trim()
            ?.trim('.')
            ?.takeIf { it.isNotBlank() }

    private fun CodeGenerationRequest.normalizedExpectedClassName(): String? =
        expectedClassName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace('$', '.')

    private fun CodeGenerationRequest.normalizedExistingTypes(): List<String> =
        context?.existingTypes.orEmpty()
            .map { it.trim().replace('$', '.') }
            .filter { it.isNotBlank() }
            .distinct()
}