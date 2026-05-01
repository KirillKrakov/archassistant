package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.dto.GenerationResponseFactory
import com.example.archassistant.model.*
import com.example.archassistant.util.CodeCleaner
import com.example.archassistant.util.PromptFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

/**
 * Pre-Generation Strategy: архитектурные правила добавляются в промпт ДО генерации кода.
 *
 * Преимущества:
 * - Быстро (1 итерация, нет перегенерации)
 * - Минимальная нагрузка на LLM API
 *
 * Недостатки:
 * - LLM может игнорировать правила в промпте
 * - Нет гарантии соответствия архитектурным стандартам
 *
 * Использование: когда скорость важнее гарантированного соответствия,
 * или для сбора базовых метрик перед сравнением с другими стратегиями.
 */
@Service
class PreGenerationStrategy(
    private val llmOrchestrator: LlmOrchestrator,      // FIXED: переиспользуем оркестратор
    private val ruleRepository: YamlRuleRepository,
    private val scoreCalculator: ComplianceScoreCalculator, // FIXED: обязательный параметр
    private val warningThreshold: Double = 70.0        // FIXED: настраиваемый порог
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

        // Шаг 1: Загрузка правил
        val rules = request.rules
            ?.let { ruleIds ->
                ruleRepository.load(request.projectId)?.rules?.filter { it.id in ruleIds && it.enabled }
            }
            ?: ruleRepository.load(request.projectId)?.getEnabledRules()
            ?: emptyList()

        // Шаг 2: Формирование промптов
        val systemPrompt = PromptFormatter.formatSystemPrompt(rules)
        val userPrompt = PromptFormatter.formatUserPrompt(
            originalRequest = request.prompt,
            previousErrors = emptyList(),
            codeContext = request.context?.codeSnippet
        )

        logger.debug("Pre-Strategy: System prompt (first 200 chars): ${systemPrompt.take(200)}")
        logger.debug("Pre-Strategy: User prompt (first 200 chars): ${userPrompt.take(200)}")
        logger.debug("Pre-Strategy: {} rules injected", rules.size)

        // Шаг 3: Генерация кода с обработкой ошибок и замером времени
        var result: CodeGenerationResponse
        val generationTime = measureTimeMillis {
            result = try {
                val generatedCode = llmOrchestrator.generateCodeRaw(
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    maxRetries = request.maxIterations
                )
                // Успех – временный ответ (score и warnings будут добавлены позже)
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
            } catch (e: LlmGenerationException) {
                GenerationResponseFactory.error(
                    errorCode = "LLM_ERROR",
                    message = e.message ?: "Failed to generate code",
                    totalTimeMs = 0
                )
            } catch (e: Exception) {
                GenerationResponseFactory.error(
                    errorCode = "INTERNAL_ERROR",
                    message = e.message ?: "Unexpected error during generation",
                    totalTimeMs = 0
                )
            }
        }

        // Если генерация завершилась с ошибкой, сразу возвращаем результат с актуальным временем
        if (!result.success) {
            return result.copy(metadata = result.metadata.copy(totalTimeMs = generationTime))
        }

        // Извлекаем сгенерированный код из успешного ответа
        val rawCode = result.data!!.code
        val generatedCode = CodeCleaner.cleanCode(rawCode)

        // Шаг 4: Опциональная валидация (отдельный замер)
        var validationTime: Long = 0
        var score: ComplianceScore? = null
        val warnings = mutableListOf<String>()

        if (request.collectMetrics) {
            val className = request.expectedClassName ?: extractClassName(generatedCode)

            if (className != null) {
                validationTime = measureTimeMillis {
                    score = scoreCalculator.calculate(
                        code = generatedCode,
                        className = className,
                        rules = rules,
                        classpath = request.classpath ?: ""
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

        // Шаг 5: Предупреждение о природе Pre-стратегии
        if (rules.isNotEmpty()) {
            warnings.add(
                "Pre-Strategy: rules are added to prompt but compliance is not enforced. " +
                        "Use Post/Hybrid for strict validation."
            )
        }

        // Шаг 6: Логирование итогового времени
        logger.info(
            "Pre-Strategy completed: generation=${generationTime}ms, validation=${validationTime}ms, " +
                    "score=${score?.total ?: "N/A"}%"
        )

        // Шаг 7: Возврат успешного ответа с актуальными данными и временем
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

    /**
     * Извлечение имени класса из сгенерированного кода (для валидации)
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
}