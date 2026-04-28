package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.dto.GenerationResponseFactory
import com.example.archassistant.model.*
import com.example.archassistant.util.ErrorFormatter
import com.example.archassistant.util.PromptFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class PostGenerationStrategy(
    private val llmOrchestrator: LlmOrchestrator,
    private val ruleRepository: YamlRuleRepository,
    private val scoreCalculator: ComplianceScoreCalculator,
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

        // Шаг 1: Загрузка правил
        val rules = request.rules
            ?.let { ruleIds ->
                ruleRepository.load(request.projectId)?.rules?.filter { it.id in ruleIds && it.enabled }
            }
            ?: ruleRepository.load(request.projectId)?.getEnabledRules()
            ?: emptyList()

        // Базовые промпты (без правил — они добавляются только при перегенерации)
        val baseSystemPrompt = PromptFormatter.formatSystemPrompt(emptyList())
        val baseUserPrompt = PromptFormatter.formatUserPrompt(
            originalRequest = request.prompt,
            previousErrors = emptyList(),
            codeContext = request.context?.codeSnippet
        )

        var iteration = 0
        var lastCode: String? = null
        var lastScore: ComplianceScore? = null
        var lastViolations: List<Violation> = emptyList()
        var totalGenerationTime: Long = 0
        var totalValidationTime: Long = 0
        val extraWarnings = mutableListOf<String>()   // ← дополнительные предупреждения (например, при пропуске валидации)

        // Шаг 2: Цикл генерации + валидации
        while (iteration < request.maxIterations) {
            iteration++
            logger.info("Post-Strategy: Iteration $iteration/${request.maxIterations}")

            // 2a. Формирование промпта для этой итерации
            val (systemPrompt, userPrompt) = if (iteration == 1) {
                // Первая итерация: базовые промпты
                baseSystemPrompt to baseUserPrompt
            } else {
                // Последующие итерации: добавляем ошибки ТОЛЬКО через ErrorFormatter (без дублирования)
                val errorSection = ErrorFormatter.formatFixInstruction(lastViolations)
                val enhancedUserPrompt = PromptFormatter.formatUserPrompt(
                    originalRequest = request.prompt,
                    previousErrors = emptyList(),
                    codeContext = request.context?.codeSnippet
                )
                baseSystemPrompt to "$enhancedUserPrompt\n\n$errorSection"
            }

            logger.debug("Post-Strategy: System prompt (first 200 chars): ${systemPrompt.take(200)}")
            logger.debug("Post-Strategy: User prompt (first 200 chars): ${userPrompt.take(200)}")

            // 2b. Генерация кода (замер времени)
            val generatedCode: String
            val generationTime = measureTimeMillis {
                generatedCode = try {
                    llmOrchestrator.generateCodeRaw(
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxRetries = 2 // Увеличено для надёжности
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
            totalGenerationTime += generationTime
            lastCode = generatedCode

            // 2c. Валидация кода (замер времени, обработка исключений)
            val className = request.expectedClassName ?: extractClassName(generatedCode)
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
                            classpath = request.classpath ?: ""
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

            // 2d. Проверка результата
            if (lastViolations.isEmpty() && (lastScore?.total ?: 0.0) >= warningThreshold) {
                // Успех: код прошёл валидацию и достиг порога
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

            // Не успех: продолжаем цикл, если есть итерации
            logger.debug(
                "Post-Strategy: Iteration $iteration failed: score=${lastScore?.total}%, " +
                        "violations=${lastViolations.size}. Retrying..."
            )
        }

        // Шаг 3: Исчерпаны итерации — возвращаем последний результат с предупреждением
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

    /**
     * Создание успешного ответа с объединением стандартных предупреждений и дополнительных.
     */
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

    /**
     * Извлечение имени класса из кода (аналогично PreGenerationStrategy)
     */
    private fun extractClassName(code: String): String? {
        val pattern = Regex("""(?:public\s+)?(?:private\s+)?(?:protected\s+)?(?:abstract\s+)?(?:final\s+)?(?:sealed\s+)?(?:data\s+)?class\s+(\w+)""")

        return pattern.find(code)?.groupValues?.get(1)
            ?: code.lines()
                .firstOrNull { line ->
                    line.contains("class ") && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("/*")
                }
                ?.substringAfter("class ")
                ?.substringBefore(' ')
                ?.substringBefore('{')
                ?.takeIf { it.isNotBlank() && it !in listOf("class", "data", "sealed", "abstract") }
    }

    /**
     * Формирование стандартных предупреждений для ответа
     */
    private fun buildWarnings(
        rules: List<ArchitecturalRule>,
        score: ComplianceScore?,
        iterations: Int,
        maxIterations: Int
    ): List<String> {
        val warnings = mutableListOf<String>()

        // Предупреждение о количестве итераций
        if (iterations > 1) {
            warnings.add("Post-Strategy required $iterations/$maxIterations iterations to achieve compliance.")
        }

        // Предупреждение о низком скоре
        if (score != null && score.total < warningThreshold) {
            warnings.add(
                "Compliance Score ${score.total}% is below threshold $warningThreshold%. " +
                        "Consider reviewing the code manually or using Hybrid strategy."
            )
        }

        return warnings
    }
}