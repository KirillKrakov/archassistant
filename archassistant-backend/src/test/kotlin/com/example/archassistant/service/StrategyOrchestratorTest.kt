package com.example.archassistant.service

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.model.StrategyType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class StrategyOrchestratorTest {

    @Mock
    private lateinit var preStrategy: PreGenerationStrategy

    private lateinit var orchestrator: StrategyOrchestrator

    @BeforeEach
    fun setUp() {
        val strategies = mapOf(StrategyType.PRE to preStrategy)
        orchestrator = StrategyOrchestrator(strategies)
    }

    @Test
    fun `generate delegates to correct strategy`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "test",
            strategy = StrategyType.PRE
        )

        val mockResponse = com.example.archassistant.dto.CodeGenerationResponse(
            success = true,
            data = null,
            error = null,
            metadata = com.example.archassistant.dto.ResponseMetadata(0, 0, 0)
        )

        whenever(preStrategy.generate(request)).thenReturn(mockResponse)

        // Act
        val response = orchestrator.generate(request)

        // Assert
        verify(preStrategy).generate(request)
        assertEquals(mockResponse, response)
    }

    @Test
    fun `generate throws on unknown strategy`() {
        // Arrange
        val request = CodeGenerationRequest(
            prompt = "Test",
            projectId = "test",
            strategy = StrategyType.POST // Не зарегистрирована
        )

        // Act & Assert
        assertThrows<IllegalArgumentException> {
            orchestrator.generate(request)
        }
    }

    @Test
    fun `getAvailableStrategies returns registered strategies`() {
        // Act
        val available = orchestrator.getAvailableStrategies("any-project")

        // Assert
        assertTrue(available.contains(StrategyType.PRE))
        // POST и HYBRID пока не зарегистрированы
        assertFalse(available.contains(StrategyType.POST))
        assertFalse(available.contains(StrategyType.HYBRID))
    }
}