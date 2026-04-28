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

/**
 * Hybrid-Generation Strategy: комбинация Pre + Post подходов.
 *
 * Алгоритм:
 * 1. Правила добавляются в system prompt (как в Pre)
 * 2. Код генерируется
 * 3. Код валидируется (как в Post)
 * 4. При нарушениях — перегенерация с обратной связью
 * 5. Цикл повторяется до успеха или исчерпания итераций
 *
 * Преимущества:
 * - Быстрее чистой Post-стратегии (правила в промпте уменьшают число ошибок)
 * - Надёжнее чистой Pre-стратегии (валидация гарантирует соответствие)
 * - Гибкая настройка через maxIterations и warningThreshold
 *
 * Недостатки:
 * - Сложнее в отладке (два механизма контроля)
 * - Выше затраты на LLM API при множественных итерациях
 *
 * Использование: когда нужно максимальное соответствие архитектурным стандартам
 * при приемлемом времени генерации.
 */
@Service
class HybridGenerationStrategy(
    private val llmOrchestrator: LlmOrchestrator,
    private val ruleRepository: YamlRuleRepository,
    private val scoreCalculator: ComplianceScoreCalculator,
    private val warningThreshold: Double = 70.0
) : CodeGenerationStrategy {

    private val logger = LoggerFactory.getLogger(HybridGenerationStrategy::class.java)
    override val strategyType: StrategyType = StrategyType.HYBRID

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

        // Базовые промпты: правила уже в system prompt (Pre-компонент)
        val baseSystemPrompt = PromptFormatter.formatSystemPrompt(rules)
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
        val extraWarnings = mutableListOf<String>()

        // Шаг 2: Цикл генерации + валидации (Post-компонент)
        while (iteration < request.maxIterations) {
            iteration++
            logger.info("Hybrid-Strategy: Iteration $iteration/${request.maxIterations}")

            // 2a. Формирование промпта для этой итерации
            val (systemPrompt, userPrompt) = if (iteration == 1) {
                // Первая итерация: базовые промпты (правила уже в system prompt)
                baseSystemPrompt to baseUserPrompt
            } else {
                // Последующие итерации: добавляем ошибки через ErrorFormatter
                val errorSection = ErrorFormatter.formatFixInstruction(lastViolations)
                val enhancedUserPrompt = PromptFormatter.formatUserPrompt(
                    originalRequest = request.prompt,
                    previousErrors = emptyList(), // Не дублируем ошибки
                    codeContext = request.context?.codeSnippet
                )
                baseSystemPrompt to "$enhancedUserPrompt\n\n$errorSection"
            }

            logger.debug("Hybrid-Strategy: System prompt (first 200 chars): ${systemPrompt.take(200)}")
            logger.debug("Hybrid-Strategy: User prompt (first 200 chars): ${userPrompt.take(200)}")

            // 2b. Генерация кода (замер времени)
            val generatedCode: String
            val generationTime = measureTimeMillis {
                generatedCode = try {
                    llmOrchestrator.generateCodeRaw(
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxRetries = 2
                    )
                } catch (e: Exception) {
                    logger.error("Hybrid-Strategy: Generation failed at iteration $iteration: ${e.message}")
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
                    logger.warn("Hybrid-Strategy: Could not extract class name, skipping validation")
                    extraWarnings.add("Could not extract class name from generated code; validation skipped.")
                    lastScore = null
                    lastViolations = emptyList()
                }
            } catch (e: Exception) {
                logger.error("Hybrid-Strategy: Validation failed at iteration $iteration", e)
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
                    "Hybrid-Strategy: Success at iteration $iteration: score=${lastScore?.total}%, " +
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
                "Hybrid-Strategy: Iteration $iteration failed: score=${lastScore?.total}%, " +
                        "violations=${lastViolations.size}. Retrying..."
            )
        }

        // Шаг 3: Исчерпаны итерации — возвращаем последний результат с предупреждением
        logger.warn(
            "Hybrid-Strategy: Exhausted $iteration iterations. Best score: ${lastScore?.total ?: "N/A"}%, " +
                    "violations: ${lastViolations.size}"
        )

        extraWarnings.add(
            "Hybrid-Strategy: Could not achieve compliance score >= $warningThreshold% " +
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
     * Создание успешного ответа с объединением предупреждений
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
            strategy = StrategyType.HYBRID,
            iterations = iterations,
            generationTimeMs = totalGenTime,
            validationTimeMs = totalValTime,
            model = llmOrchestrator.extractModelName(),
            warnings = allWarnings
        )
    }

    /**
     * Извлечение имени класса из кода
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
     * Формирование стандартных предупреждений
     */
    private fun buildWarnings(
        rules: List<ArchitecturalRule>,
        score: ComplianceScore?,
        iterations: Int,
        maxIterations: Int
    ): List<String> {
        val warnings = mutableListOf<String>()

        if (iterations > 1) {
            warnings.add("Hybrid-Strategy required $iterations/$maxIterations iterations to achieve compliance.")
        }

        if (score != null && score.total < warningThreshold) {
            warnings.add(
                "Compliance Score ${score.total}% is below threshold $warningThreshold%. " +
                        "Consider reviewing the code manually."
            )
        }

        return warnings
    }
}