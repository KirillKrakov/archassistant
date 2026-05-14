package com.example.archassistant.service.generation.strategy

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.dto.GenerationResponseFactory
import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.ComplianceScore
import com.example.archassistant.model.StrategyType
import com.example.archassistant.model.Violation
import com.example.archassistant.service.validation.ComplianceScoreCalculator
import com.example.archassistant.service.generation.LlmOrchestrator
import com.example.archassistant.service.context.ProjectContextService
import com.example.archassistant.service.rules.repository.YamlRuleRepository
import com.example.archassistant.util.CodeCleaner
import com.example.archassistant.util.GeneratedTypeNameExtractor
import com.example.archassistant.util.ProjectImportNormalizer
import org.slf4j.LoggerFactory

abstract class BaseGenerationStrategy(
    protected val llmOrchestrator: LlmOrchestrator,
    protected val ruleRepository: YamlRuleRepository,
    protected val scoreCalculator: ComplianceScoreCalculator,
    protected val projectContextService: ProjectContextService,
    protected val properties: ArchassistantProperties
) {

    protected val logger = LoggerFactory.getLogger(javaClass)
    protected val warningThreshold: Double = properties.compliance.threshold

    protected data class PreparedRequest(
        val projectContext: com.example.archassistant.model.ProjectContextSnapshot,
        val rules: List<ArchitecturalRule>,
        val normalizedTargetPackage: String?,
        val normalizedExpectedClassName: String?,
        val normalizedExistingTypes: List<String>
    )

    protected data class ValidationOutcome(
        val score: ComplianceScore?,
        val violations: List<Violation>,
        val validationTimeMs: Long
    )

    protected fun prepare(request: CodeGenerationRequest): PreparedRequest {
        if (request.prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }

        val projectContext = projectContextService.requireProjectContext(
            projectId = request.projectId,
            refresh = false,
            projectPathOverride = null
        )

        val effectiveRules = resolveRules(request)

        return PreparedRequest(
            projectContext = projectContext,
            rules = effectiveRules,
            normalizedTargetPackage = request.normalizedTargetPackage(),
            normalizedExpectedClassName = request.normalizedExpectedClassName(),
            normalizedExistingTypes = request.normalizedExistingTypes()
        )
    }

    protected fun resolveRules(request: CodeGenerationRequest): List<ArchitecturalRule> {
        val configRules = ruleRepository.load(request.projectId)?.getEnabledRules().orEmpty()

        val selected = request.rules
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.let { ids -> configRules.filter { it.id in ids } }

        return selected ?: configRules
    }

    protected fun cleanAndNormalizeCode(
        rawCode: String,
        projectContext: com.example.archassistant.model.ProjectContextSnapshot,
        primaryTypeName: String?
    ): String {
        return ProjectImportNormalizer.normalize(
            code = CodeCleaner.cleanCode(rawCode),
            projectContext = projectContext,
            primaryTypeName = primaryTypeName
        )
    }

    protected fun extractPrimaryTypeName(
        request: CodeGenerationRequest,
        generatedCode: String,
        rawCode: String? = null
    ): String? {
        return request.expectedClassName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace('$', '.')
            ?: GeneratedTypeNameExtractor.extract(generatedCode)?.replace('$', '.')
            ?: rawCode?.let { GeneratedTypeNameExtractor.extract(it)?.replace('$', '.') }
    }

    protected fun validateGeneratedCode(
        generatedCode: String,
        className: String?,
        rules: List<ArchitecturalRule>,
        classpath: String,
        projectContext: com.example.archassistant.model.ProjectContextSnapshot
    ): ValidationOutcome {
        if (className == null) {
            return ValidationOutcome(null, emptyList(), 0)
        }

        var score: ComplianceScore? = null
        var violations: List<Violation> = emptyList()
        var validationTimeMs = 0L

        val measuredScore = kotlin.system.measureTimeMillis {
            score = scoreCalculator.calculate(
                code = generatedCode,
                className = className,
                rules = rules,
                classpath = classpath,
                projectContext = projectContext
            )
            violations = score?.violations.orEmpty()
        }

        validationTimeMs = measuredScore

        return ValidationOutcome(score, violations, validationTimeMs)
    }

    protected fun CodeGenerationRequest.normalizedTargetPackage(): String? =
        context?.targetPackage
            ?.trim()
            ?.trim('.')
            ?.takeIf { it.isNotBlank() }

    protected fun CodeGenerationRequest.normalizedExpectedClassName(): String? =
        expectedClassName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace('$', '.')

    protected fun CodeGenerationRequest.normalizedExistingTypes(): List<String> =
        context?.existingTypes.orEmpty()
            .map { it.trim().replace('$', '.') }
            .filter { it.isNotBlank() }
            .distinct()

    protected fun success(
        code: String,
        score: ComplianceScore?,
        strategy: StrategyType,
        iterations: Int,
        generationTimeMs: Long,
        validationTimeMs: Long,
        warnings: List<String> = emptyList()
    ): CodeGenerationResponse {
        return GenerationResponseFactory.success(
            code = code,
            score = score,
            strategy = strategy,
            iterations = iterations,
            generationTimeMs = generationTimeMs,
            validationTimeMs = validationTimeMs,
            model = llmOrchestrator.extractModelName(),
            warnings = warnings
        )
    }

    protected fun error(
        code: String,
        message: String,
        totalTimeMs: Long = 0
    ): CodeGenerationResponse =
        GenerationResponseFactory.error(code, message, details = null, totalTimeMs)
}