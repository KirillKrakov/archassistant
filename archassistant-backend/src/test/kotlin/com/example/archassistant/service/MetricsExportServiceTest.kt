package com.example.archassistant.service

import com.example.archassistant.dto.ExportFormat
import com.example.archassistant.dto.ExportRequest
import com.example.archassistant.model.GenerationRecord
import com.example.archassistant.model.StrategyType
import com.example.archassistant.repository.GenerationRecordRepository
import com.example.archassistant.util.CsvExporter
import com.example.archassistant.util.JsonExporter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class MetricsExportServiceTest {

    @Mock
    private lateinit var recordRepository: GenerationRecordRepository

    @Mock
    private lateinit var csvExporter: CsvExporter

    @Mock
    private lateinit var jsonExporter: JsonExporter

    private lateinit var exportService: MetricsExportService

    @BeforeEach
    fun setUp() {
        exportService = MetricsExportService(recordRepository, csvExporter, jsonExporter)
    }

    @Test
    fun `export returns CSV when requested`() {
        // Arrange
        val request = ExportRequest(format = ExportFormat.CSV, projectId = "test")
        val records = listOf(
            GenerationRecord(
                id = "1",
                projectId = "test",
                strategy = StrategyType.PRE,
                success = true,
                scoreTotal = 85.0,
                iterations = 1,
                generationTimeMs = 1000,
                validationTimeMs = 200,
                violationsCount = 0
            )
        )

        whenever(recordRepository.findByProjectIdOrderByCreatedAtDesc("test")).thenReturn(records)
        whenever(csvExporter.export(records, false)).thenReturn("id,projectId,strategy\n1,test,PRE")

        // Act
        val result = exportService.export(request)

        // Assert
        assertEquals(ExportFormat.CSV, result.format)
        assertEquals(1, result.recordCount)
        assertTrue(result.content.contains("id,projectId,strategy"))
    }

    @Test
    fun `export returns JSON when requested`() {
        // Arrange
        val request = ExportRequest(format = ExportFormat.JSON, projectId = "test")
        val records = listOf(
            GenerationRecord(
                id = "1",
                projectId = "test",
                strategy = StrategyType.PRE,
                success = true
            )
        )

        whenever(recordRepository.findByProjectIdOrderByCreatedAtDesc("test")).thenReturn(records)
        whenever(jsonExporter.export(records, false)).thenReturn("""[{"id":"1"}]""")

        // Act
        val result = exportService.export(request)

        // Assert
        assertEquals(ExportFormat.JSON, result.format)
        assertEquals("""[{"id":"1"}]""", result.content)
    }
}