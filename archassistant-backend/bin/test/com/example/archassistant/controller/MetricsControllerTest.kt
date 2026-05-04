package com.example.archassistant.controller

import com.example.archassistant.dto.StrategyMetrics
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.mockito.kotlin.*
import java.time.LocalDateTime

@WebMvcTest(MetricsController::class)
class MetricsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var metricsRepository: GenerationRecordRepository

    @Test
    fun `compareStrategies returns metrics for all strategies`() {
        val mockMetrics = listOf(
            StrategyMetrics(StrategyType.PRE, 65.3, 1.0, 0.45, 1200.0),
            StrategyMetrics(StrategyType.POST, 78.1, 2.1, 0.72, 2800.0),
            StrategyMetrics(StrategyType.HYBRID, 82.7, 1.8, 0.81, 2400.0)
        )

        whenever(metricsRepository.getAllMetrics()).thenReturn(mockMetrics)

        mockMvc.get("/api/metrics/strategies")
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.PRE.avgScore", equalTo(65.3))
                    jsonPath("$.POST.avgIterations", equalTo(2.1))
                    jsonPath("$.HYBRID.successRate", equalTo(0.81))
                }
            }
    }

    @Test
    fun `compareStrategies filters by projectId when provided`() {
        val projectId = "test-project"
        val mockMetrics = listOf(StrategyMetrics(StrategyType.HYBRID, 90.0, 1.5, 0.95, 1500.0))

        whenever(metricsRepository.getMetricsByProject(projectId)).thenReturn(mockMetrics)

        mockMvc.get("/api/metrics/strategies?projectId={projectId}", projectId)
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.HYBRID.avgScore", equalTo(90.0))
                    jsonPath("$.PRE").doesNotExist()
                }
            }
    }

    @Test
    fun `getProjectMetrics returns statistics for project`() {
        val projectId = "test-project"
        val history = listOf(
            GenerationRecord(
                id = "test-id",
                projectId = projectId,
                strategy = StrategyType.HYBRID,
                success = true,
                scoreTotal = 85.0,
                iterations = 2,
                createdAt = LocalDateTime.now()
            )
        )

        whenever(metricsRepository.countByProjectId(projectId)).thenReturn(1L)
        whenever(metricsRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(history)

        mockMvc.get("/api/metrics/{projectId}", projectId)
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.totalGenerations", equalTo(1))
                    jsonPath("$.avgScore", equalTo(85.0))
                    jsonPath("$.recentHistory[0].strategy", equalTo("HYBRID"))
                }
            }
    }

    @Test
    fun `saveGenerationRecord persists new record`() {
        val recordDto = GenerationRecordDto(
            id = "saved-id",
            projectId = "test",
            strategy = StrategyType.PRE,
            success = true,
            scoreTotal = 90.0
        )

        val capturedRecord = argumentCaptor<GenerationRecord>()
        whenever(metricsRepository.save(capturedRecord.capture())).thenAnswer {
            capturedRecord.firstValue.copy(id = "saved-id")
        }

        mockMvc.post("/api/metrics/record") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(recordDto)
        }.andExpect {
            status { isOk() }
            jsonPath("$.success", equalTo(true))
            jsonPath("$.recordId", equalTo("saved-id"))
        }

        // Дополнительно можно проверить, что переданный record имеет правильные поля
        val savedRecord = capturedRecord.firstValue
        assertEquals("test", savedRecord.projectId)
        assertEquals(StrategyType.PRE, savedRecord.strategy)
        assertEquals(true, savedRecord.success)
        assertEquals(90.0, savedRecord.scoreTotal)
    }

    @Test
    fun `saveGenerationRecord returns error on exception`() {
        val recordDto = GenerationRecordDto(
            projectId = "test",
            strategy = StrategyType.PRE,
            success = true
        )

        whenever(metricsRepository.save(any<GenerationRecord>())).thenThrow(RuntimeException("DB error"))

        mockMvc.post("/api/metrics/record") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(recordDto)
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.error").exists()
        }
    }
}