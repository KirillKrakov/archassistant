package com.example.archassistant.service.generation.pipeline

import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.dto.generation.response.GenerationResponseFactory
import com.example.archassistant.model.core.ComplianceScore
import com.example.archassistant.model.core.StrategyType
import org.springframework.stereotype.Service

@Service
class GenerationResponseAssembler {

    fun success(
        code: String,
        score: ComplianceScore?,
        strategy: StrategyType,
        iterations: Int,
        generationTimeMs: Long,
        validationTimeMs: Long,
        modelName: String? = null,
        warnings: List<String> = emptyList()
    ): CodeGenerationResponse {
        return GenerationResponseFactory.success(
            code = code,
            score = score,
            strategy = strategy,
            iterations = iterations,
            generationTimeMs = generationTimeMs,
            validationTimeMs = validationTimeMs,
            model = modelName,
            warnings = warnings
        )
    }

    fun error(
        errorCode: String,
        message: String,
        totalTimeMs: Long = 0
    ): CodeGenerationResponse {
        return GenerationResponseFactory.error(
            errorCode = errorCode,
            message = message,
            details = null,
            totalTimeMs = totalTimeMs
        )
    }
}