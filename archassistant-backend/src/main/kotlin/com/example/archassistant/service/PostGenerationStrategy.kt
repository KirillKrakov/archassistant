package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.dto.GenerationResponseFactory
import com.example.archassistant.model.*
import com.example.archassistant.util.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class PostGenerationStrategy(
    private val llmOrchestrator: LlmOrchestrator,
    private val ruleRepository: YamlRuleRepository,
    private val scoreCalculator: ComplianceScoreCalculator,
    private val projectContextService: ProjectContextService,
    private val warningThreshold: Double = 70.0
) : CodeGenerationStrategy {

    private val logger = LoggerFactory.getLogger(PostGenerationStrategy::class.java)
    override val strategyType: StrategyType = StrategyType.POST

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

        val baseSystemPrompt = PromptFormatter.formatSystemPrompt(
            rules = emptyList(),
            languageHint = projectContext.preferredLanguageHint()
        )

        val requestContextText = projectContext.promptContext(
            requestText = request.prompt,
            targetPackage = request.context?.targetPackage,
            expectedClassName = request.expectedClassName,
            existingTypes = request.context?.existingTypes.orEmpty()
        )

        val baseUserPrompt = PromptFormatter.formatUserPrompt(
            originalRequest = request.prompt,
            previousErrors = emptyList(),
            projectContext = requestContextText,
            codeContext = request.context?.codeSnippet
        )

        var iteration = 0
        var lastCode: String? = null
        var lastScore: ComplianceScore? = null
        var lastViolations: List<Violation> = emptyList()
        var totalGenerationTime: Long = 0
        var totalValidationTime: Long = 0
        val extraWarnings = mutableListOf<String>()

        while (iteration < request.maxIterations) {
            iteration++
            logger.info("Post-Strategy: Iteration $iteration/${request.maxIterations}")

            val (systemPrompt, userPrompt) = if (iteration == 1) {
                baseSystemPrompt to baseUserPrompt
            } else {
                val errorSection = ErrorFormatter.formatFixInstruction(lastViolations)
                val enhancedUserPrompt = PromptFormatter.formatUserPrompt(
                    originalRequest = request.prompt,
                    previousErrors = emptyList(),
                    projectContext = requestContextText,
                    codeContext = request.context?.codeSnippet
                )
                baseSystemPrompt to "$enhancedUserPrompt\n\n$errorSection"
            }

            logger.debug("Post-Strategy: System prompt (first 200 chars): ${systemPrompt.take(200)}")
            logger.debug("Post-Strategy: User prompt (first 200 chars): ${userPrompt.take(200)}")

            val rawCode: String
            val generationTime = measureTimeMillis {
                rawCode = try {
                    llmOrchestrator.generateCodeRaw(
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxRetries = 2
                    )
                } catch (e: Exception) {
                    logger.error("Post-Strategy: Generation failed at iteration $iteration: ${e.message}")
                    return GenerationResponseFactory.error(
                        errorCode = "LLM_ERROR",
                        message = "Failed at iteration $iteration: ${e.message}",
                        totalTimeMs = totalGenerationTime + totalValidationTime
                    )
                }
            }
            val generatedCode = ProjectImportNormalizer.normalize(
                code = CodeCleaner.cleanCode(rawCode),
                projectContext = projectContext,
                primaryTypeName = request.expectedClassName ?: GeneratedTypeNameExtractor.extract(rawCode)
            )
            totalGenerationTime += generationTime
            lastCode = generatedCode

            val className = request.expectedClassName ?: GeneratedTypeNameExtractor.extract(generatedCode)
            var validationTime = 0L
            var score: ComplianceScore?
            var violations: List<Violation>

            try {
                if (className != null) {
                    validationTime = measureTimeMillis {
                        score = scoreCalculator.calculate(
                            code = generatedCode,
                            className = className,
                            rules = rules,
                            classpath = request.classpath ?: "",
                            projectContext = projectContext
                        )
                        violations = score?.violations ?: emptyList()
                    }
                    totalValidationTime += validationTime
                    lastScore = score
                    lastViolations = violations
                } else {
                    logger.warn("Post-Strategy: Could not extract class name, skipping validation")
                    extraWarnings.add("Could not extract class name from generated code; validation skipped.")
                    lastScore = null
                    lastViolations = emptyList()
                }
            } catch (e: Exception) {
                logger.error("Post-Strategy: Validation failed at iteration $iteration", e)
                return GenerationResponseFactory.error(
                    errorCode = "VALIDATION_ERROR",
                    message = "Validation failed at iteration $iteration: ${e.message}",
                    totalTimeMs = totalGenerationTime + totalValidationTime
                )
            }

            if (lastViolations.isEmpty() && (lastScore?.total ?: 0.0) >= warningThreshold) {
                logger.info(
                    "Post-Strategy: Success at iteration $iteration: score=${lastScore?.total}%, " +
                            "violations=${lastViolations.size}, generation=${generationTime}ms, validation=${validationTime}ms"
                )

                return createSuccessResponse(
                    code = generatedCode,
                    score = lastScore,
                    iterations = iteration,
                    totalGenTime = totalGenerationTime,
                    totalValTime = totalValidationTime,
                    rules = rules,
                    maxIterations = request.maxIterations,
                    extraWarnings = extraWarnings
                )
            }

            logger.debug(
                "Post-Strategy: Iteration $iteration failed: score=${lastScore?.total}%, " +
                        "violations=${lastViolations.size}. Retrying..."
            )
        }

        logger.warn(
            "Post-Strategy: Exhausted $iteration iterations. Best score: ${lastScore?.total ?: "N/A"}%, " +
                    "violations: ${lastViolations.size}"
        )

        extraWarnings.add(
            "Post-Strategy: Could not achieve compliance score >= $warningThreshold% " +
                    "after $iteration iterations. Code may require manual review."
        )

        return createSuccessResponse(
            code = lastCode ?: "",
            score = lastScore,
            iterations = iteration,
            totalGenTime = totalGenerationTime,
            totalValTime = totalValidationTime,
            rules = rules,
            maxIterations = request.maxIterations,
            extraWarnings = extraWarnings
        )
    }

    private fun createSuccessResponse(
        code: String,
        score: ComplianceScore?,
        iterations: Int,
        totalGenTime: Long,
        totalValTime: Long,
        rules: List<ArchitecturalRule>,
        maxIterations: Int,
        extraWarnings: List<String> = emptyList()
    ): CodeGenerationResponse {
        val standardWarnings = buildWarnings(rules, score, iterations, maxIterations)
        val allWarnings = standardWarnings + extraWarnings
        return GenerationResponseFactory.success(
            code = code,
            score = score,
            strategy = StrategyType.POST,
            iterations = iterations,
            generationTimeMs = totalGenTime,
            validationTimeMs = totalValTime,
            model = llmOrchestrator.extractModelName(),
            warnings = allWarnings
        )
    }

    private fun buildWarnings(
        rules: List<ArchitecturalRule>,
        score: ComplianceScore?,
        iterations: Int,
        maxIterations: Int
    ): List<String> {
        val warnings = mutableListOf<String>()

        if (iterations > 1) {
            warnings.add("Post-Strategy required $iterations/$maxIterations iterations to achieve compliance.")
        }

        if (score != null && score.total < warningThreshold) {
            warnings.add(
                "Compliance Score ${score.total}% is below threshold $warningThreshold%. " +
                        "Consider reviewing the code manually or using Hybrid strategy."
            )
        }

        return warnings
    }
}