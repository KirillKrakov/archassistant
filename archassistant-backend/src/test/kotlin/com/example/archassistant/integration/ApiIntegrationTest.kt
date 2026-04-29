package com.example.archassistant.integration

import com.example.archassistant.dto.CodeGenerationRequest
import com.example.archassistant.dto.ValidationRequest
import com.example.archassistant.model.*
import com.example.archassistant.service.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles  // ← Добавить импорт
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.mockito.kotlin.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

// ← Добавить аннотацию профиля
@SpringBootTest
@ActiveProfiles("test")  // ← Использовать application-test.yml
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var strategyOrchestrator: StrategyOrchestrator

    @MockBean
    private lateinit var ruleRepository: YamlRuleRepository

    @MockBean
    private lateinit var validator: DynamicRuleValidator

    @MockBean
    private lateinit var scoreCalculator: ComplianceScoreCalculator

    @BeforeEach
    fun setUp() {
        // Настройка моков по умолчанию
        whenever(ruleRepository.load(any())).thenReturn(null)
    }

    @Test
    fun `full flow save rules to generate code to validate result`() {
        val projectId = "integration-test"

        // 1. Сохраняем правила
        val rulesConfig = RulesConfig(
            projectId = projectId,
            rules = listOf(
                ArchitecturalRule(
                    id = "suffix",
                    name = "Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    enabled = true
                )
            )
        )
        whenever(ruleRepository.validate(rulesConfig)).thenReturn(ValidationResult.success())
        whenever(ruleRepository.save(rulesConfig)).thenReturn(true)

        mockMvc.post("/api/rules/{projectId}", projectId) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(rulesConfig)
        }
            .andExpect { status { isOk() } }

        // 2. Генерируем код
        val generationRequest = CodeGenerationRequest(
            prompt = "Create UserService",
            projectId = projectId,
            strategy = StrategyType.PRE,
            expectedClassName = "UserService"
        )

        val mockScore = ComplianceScore(total = 100.0, rulesPass = 100.0, patternMatch = 100.0, dependencyCorrect = 100.0)
        whenever(strategyOrchestrator.generate(generationRequest)).thenReturn(
            com.example.archassistant.dto.CodeGenerationResponse(
                success = true,
                data = com.example.archassistant.dto.GenerationData(
                    code = "public class UserService {}",
                    score = mockScore,
                    strategy = StrategyType.PRE,
                    iterations = 1
                ),
                metadata = com.example.archassistant.dto.ResponseMetadata(100, 50, 150)
            )
        )

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(generationRequest)
        }
            .andExpect {
                status { isOk() }
                content { jsonPath("$.success").value(true) }
            }

        // 3. Валидируем результат
        val validationRequest = ValidationRequest(
            code = "public class UserService {}",
            className = "UserService",
            projectId = projectId
        )

        whenever(ruleRepository.load(projectId)).thenReturn(rulesConfig)
        whenever(validator.validate(any(), any(), any(), any())).thenReturn(ValidationResult.success())

        mockMvc.post("/api/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validationRequest)
        }
            .andExpect {
                status { isOk() }
                content { jsonPath("$.result.passed").value(true) }
            }
    }

    @Test
    fun `error handling invalid prompt returns 400`() {
        val request = CodeGenerationRequest(prompt = "", projectId = "test")

        mockMvc.post("/api/generate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isBadRequest() }
                content { jsonPath("$.error.code").value("INVALID_PROMPT") }
            }
    }

    @Test
    fun `error handling validation failure returns violations`() {
        val request = ValidationRequest(
            code = "invalid code",
            className = "Test",
            projectId = "test"
        )

        whenever(ruleRepository.load("test")).thenReturn(null)
        whenever(validator.validate(any(), any(), any(), any())).thenReturn(
            ValidationResult.failure(listOf(
                Violation("test", "Invalid code", "Test", severity = Severity.ERROR)
            ))
        )

        mockMvc.post("/api/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.result.passed").value(false)
                    jsonPath("$.result.violations[0].severity").value("ERROR")
                }
            }
    }
}