package com.example.archassistant.service.generation.pipeline

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.model.generation.GenerationValidationResult
import com.example.archassistant.model.generation.PreparedGenerationRequest
import com.example.archassistant.model.Violation
import com.example.archassistant.model.core.ComplianceScore
import com.example.archassistant.service.generation.validation.ComplianceScoreCalculator
import com.example.archassistant.util.CodeCleaner
import com.example.archassistant.util.GeneratedTypeNameExtractor
import com.example.archassistant.util.ProjectImportNormalizer
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class ValidationFacade(
    private val scoreCalculator: ComplianceScoreCalculator
) {

    fun analyze(
        request: CodeGenerationRequest,
        prepared: PreparedGenerationRequest,
        rawCode: String,
        performValidation: Boolean
    ): GenerationValidationResult {
        val primaryTypeName = extractPrimaryTypeName(request, rawCode)
        val generatedCode = cleanAndNormalizeCode(
            rawCode = rawCode,
            projectContext = prepared.projectContext,
            primaryTypeName = primaryTypeName
        )

        if (!performValidation || primaryTypeName == null) {
            return GenerationValidationResult(
                generatedCode = generatedCode,
                primaryTypeName = primaryTypeName,
                score = null,
                violations = emptyList(),
                validationTimeMs = 0L
            )
        }

        var score: ComplianceScore?
        var violations: List<Violation>

        val validationTimeMs = measureTimeMillis {
            score = scoreCalculator.calculate(
                code = generatedCode,
                className = primaryTypeName,
                rules = prepared.rules,
                classpath = request.classpath.orEmpty(),
                projectContext = prepared.projectContext
            )
            violations = score?.violations.orEmpty()
        }

        return GenerationValidationResult(
            generatedCode = generatedCode,
            primaryTypeName = primaryTypeName,
            score = score,
            violations = violations,
            validationTimeMs = validationTimeMs
        )
    }

    private fun cleanAndNormalizeCode(
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

    private fun extractPrimaryTypeName(
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
}