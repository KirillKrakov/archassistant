package com.example.archassistant.controller

import com.example.archassistant.dto.ValidationRequest
import com.example.archassistant.model.Severity
import com.example.archassistant.model.ValidationResult
import com.example.archassistant.model.Violation
import com.example.archassistant.service.DynamicRuleValidator
import com.example.archassistant.service.YamlRuleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.mockito.kotlin.*

@WebMvcTest(ValidationController::class)
class ValidationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var validator: DynamicRuleValidator

    @MockBean
    private lateinit var ruleRepository: YamlRuleRepository

    @Test
    fun `validateCode returns success for valid code`() {
        val request = ValidationRequest(
            code = "public class Test {}",
            className = "Test",
            projectId = "test"
        )

        whenever(ruleRepository.load("test")).thenReturn(null)
        whenever(validator.validate("public class Test {}", "Test", emptyList(), "")).thenReturn(
            ValidationResult.success("All rules passed")
        )

        mockMvc.post("/api/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.result.passed", equalTo(true))
                    jsonPath("$.result.message", equalTo("All rules passed"))
                }
            }
    }

    @Test
    fun `validateCode returns failure for invalid code`() {
        val request = ValidationRequest(
            code = "invalid code",
            className = "Test",
            projectId = "test"
        )

        whenever(ruleRepository.load("test")).thenReturn(null)
        whenever(validator.validate(any(), any(), any(), any())).thenReturn(
            ValidationResult.failure(listOf(Violation("test", "Invalid", "Test", severity = Severity.ERROR)))
        )

        mockMvc.post("/api/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.result.passed", equalTo(false))
                    jsonPath("$.result.violations[0].ruleId", equalTo("test"))
                }
            }
    }

    @Test
    fun `validateCode extracts class name when not provided`() {
        val request = ValidationRequest(
            code = "public class AutoDetected {}",
            className = null,
            projectId = "test"
        )

        whenever(ruleRepository.load("test")).thenReturn(null)
        whenever(validator.validate(any(), eq("AutoDetected"), any(), any())).thenReturn(
            ValidationResult.success()
        )

        mockMvc.post("/api/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content { jsonPath("$.result.passed", equalTo(true)) }
            }
    }

    @Test
    fun `validateCode returns error when class name cannot be extracted`() {
        val request = ValidationRequest(
            code = "public void method() {}",
            className = null,
            projectId = "test"
        )

        whenever(ruleRepository.load("test")).thenReturn(null)

        mockMvc.post("/api/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isBadRequest() }
                content {
                    jsonPath("$.result.passed", equalTo(false))
                    jsonPath("$.result.violations[0].ruleId", equalTo("validation_error"))
                }
            }
    }

    @Test
    fun `validateBasic returns success for compilable code`() {
        val request = ValidationRequest(
            code = "public class Test {}",
            className = "Test"
        )

        whenever(validator.validateBasic("public class Test {}", "Test")).thenReturn(
            ValidationResult.success("Code compiled successfully")
        )

        mockMvc.post("/api/validate/basic") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content { jsonPath("$.result.passed", equalTo(true)) }
            }
    }

    @Test
    fun `validateBasic returns failure for uncompilable code`() {
        val request = ValidationRequest(
            code = "invalid syntax",
            className = "Test"
        )

        whenever(validator.validateBasic("invalid syntax", "Test")).thenReturn(
            ValidationResult.failure(listOf(Violation("compilation", "Failed", "Test", severity = Severity.CRITICAL)))
        )

        mockMvc.post("/api/validate/basic") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.result.passed", equalTo(false))
                    jsonPath("$.result.violations[0].severity", equalTo("CRITICAL"))
                }
            }
    }
}