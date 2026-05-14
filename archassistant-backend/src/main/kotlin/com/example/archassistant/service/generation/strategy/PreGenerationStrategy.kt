package com.example.archassistant.service.generation.strategy

import com.example.archassistant.dto.generation.request.CodeGenerationRequest
import com.example.archassistant.dto.generation.response.CodeGenerationResponse
import com.example.archassistant.model.StrategyType
import com.example.archassistant.model.CodeGenerationStrategy
import com.example.archassistant.service.generation.pipeline.GenerationPipelineService
import org.springframework.stereotype.Service

@Service
class PreGenerationStrategy(
    protected val pipelineService: GenerationPipelineService
) : CodeGenerationStrategy {

    override val strategyType: StrategyType = StrategyType.PRE

    override fun generate(request: CodeGenerationRequest): CodeGenerationResponse {
        return pipelineService.generate(request, strategyType)
    }
}