package com.example.archassistant.controller

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.GenerationResponseFactory
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.service.StrategyOrchestrator
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.mockito.kotlin.*

@WebMvcTest(GenerationController::class)
class GenerationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var strategyOrchestrator: StrategyOrchestrator

    @MockBean
    private lateinit var metricsRepository: GenerationRecordRepository

    // ─────────────────────────────────────────────────────────────────
    // Существующие тесты
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `generate endpoint returns success response`() {
        val request = CodeGenerationRequest(prompt = "Test", projectId = "test", strategy = StrategyType.PRE)
        val mockResponse = GenerationResponseFactory.success(code = "code", score = null, strategy = StrategyType.PRE, iterations = 1, generationTimeMs = 100)

        whenever(strategyOrchestrator.generate(request)).thenReturn(mockResponse)

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.success", equalTo(true))
                    jsonPath("$.data.code", equalTo("code"))
                }
            }
    }

    @Test
    fun `generate endpoint returns error for empty prompt`() {
        val request = CodeGenerationRequest(prompt = "", projectId = "test")

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isBadRequest() }
                content {
                    jsonPath("$.success", equalTo(false))
                    jsonPath("$.error.code", equalTo("INVALID_PROMPT"))
                }
            }
        verify(strategyOrchestrator, never()).generate(any())
    }

    // ─────────────────────────────────────────────────────────────────
    // НОВЫЕ ТЕСТЫ
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `generate endpoint works with PRE strategy`() {
        val request = CodeGenerationRequest(prompt = "Test", projectId = "test", strategy = StrategyType.PRE)
        val mockResponse = GenerationResponseFactory.success(code = "pre-code", score = null, strategy = StrategyType.PRE, iterations = 1, generationTimeMs = 100)

        whenever(strategyOrchestrator.generate(request)).thenReturn(mockResponse)

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content { jsonPath("$.data.strategy", equalTo("PRE")) }
            }
    }

    @Test
    fun `generate endpoint works with POST strategy`() {
        val request = CodeGenerationRequest(prompt = "Test", projectId = "test", strategy = StrategyType.POST, maxIterations = 3)
        val mockResponse = GenerationResponseFactory.success(code = "post-code", score = null, strategy = StrategyType.POST, iterations = 2, generationTimeMs = 200)

        whenever(strategyOrchestrator.generate(request)).thenReturn(mockResponse)

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.data.strategy", equalTo("POST"))
                    jsonPath("$.data.iterations", equalTo(2))
                }
            }
    }

    @Test
    fun `generate endpoint works with HYBRID strategy`() {
        val request = CodeGenerationRequest(prompt = "Test", projectId = "test", strategy = StrategyType.HYBRID)
        val mockResponse = GenerationResponseFactory.success(code = "hybrid-code", score = null, strategy = StrategyType.HYBRID, iterations = 1, generationTimeMs = 150)

        whenever(strategyOrchestrator.generate(request)).thenReturn(mockResponse)

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content { jsonPath("$.data.strategy", equalTo("HYBRID")) }
            }
    }

    @Test
    fun `generate endpoint returns error when orchestrator throws IllegalArgumentException`() {
        val request = CodeGenerationRequest(prompt = "Test", projectId = "test", strategy = StrategyType.PRE)

        whenever(strategyOrchestrator.generate(request)).thenThrow(IllegalArgumentException("Strategy PRE not registered"))

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success", equalTo(false))
            jsonPath("$.error.code", equalTo("INVALID_REQUEST"))
            jsonPath("$.error.message", equalTo("Strategy PRE not registered"))
        }
    }

    @Test
    fun `generate endpoint returns error when orchestrator throws exception`() {
        val request = CodeGenerationRequest(prompt = "Test", projectId = "test", strategy = StrategyType.PRE)

        whenever(strategyOrchestrator.generate(request)).thenThrow(RuntimeException("Unexpected error"))

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isInternalServerError() }
                content { jsonPath("$.error.code", equalTo("INTERNAL_ERROR")) }
            }
    }

    @Test
    fun `generate health endpoint returns available strategies`() {
        whenever(strategyOrchestrator.getAvailableStrategies(any())).thenReturn(listOf(StrategyType.PRE, StrategyType.POST, StrategyType.HYBRID))

        mockMvc.get("/api/generate/health")
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.status", equalTo("UP"))
                    jsonPath("$.availableStrategies[0]", equalTo("PRE"))
                    jsonPath("$.availableStrategies[1]", equalTo("POST"))
                    jsonPath("$.availableStrategies[2]", equalTo("HYBRID"))
                }
            }
    }
}