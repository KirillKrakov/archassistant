package com.example.archassistant.service.generation.strategy

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.model.core.StrategyType
import com.example.archassistant.model.generation.CodeGenerationStrategy
import com.example.archassistant.service.generation.pipeline.GenerationPipelineService
import org.springframework.stereotype.Service

@Service
class HybridGenerationStrategy(
    protected val pipelineService: GenerationPipelineService
) : CodeGenerationStrategy {

    override val strategyType: StrategyType = StrategyType.HYBRID

    override fun generate(request: CodeGenerationRequest): CodeGenerationResponse {
        return pipelineService.generate(request, strategyType)
    }
}