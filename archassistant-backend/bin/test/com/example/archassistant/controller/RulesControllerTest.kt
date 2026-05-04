package com.example.archassistant.controller

import com.example.archassistant.model.*
import com.example.archassistant.service.ProjectStructureScanner
import com.example.archassistant.service.RuleTemplateEngine
import com.example.archassistant.service.YamlRuleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.mockito.kotlin.*

@WebMvcTest(RulesController::class)
class RulesControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var ruleRepository: YamlRuleRepository

    @MockBean
    private lateinit var templateEngine: RuleTemplateEngine

    @MockBean
    private lateinit var projectScanner: ProjectStructureScanner

    // ─────────────────────────────────────────────────────────────────
    // Существующие тесты
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `getRules returns config for existing project`() {
        val projectId = "test-project"
        val mockConfig = RulesConfig(projectId = projectId, rules = emptyList())

        whenever(ruleRepository.load(projectId)).thenReturn(mockConfig)

        mockMvc.get("/api/rules/{projectId}", projectId)
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.project_id", equalTo(projectId))
                }
            }
    }

    @Test
    fun `saveRules accepts valid configuration`() {
        val projectId = "test-project"
        val config = RulesConfig(projectId = projectId, rules = emptyList())

        whenever(ruleRepository.validate(config)).thenReturn(ValidationResult.success())
        whenever(ruleRepository.save(config)).thenReturn(true)

        mockMvc.post("/api/rules/{projectId}", projectId) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(config)
        }
            .andExpect {
                status { isOk() }
                content { jsonPath("$.success", equalTo(true))
            }
                }
    }

    // ─────────────────────────────────────────────────────────────────
    // НОВЫЕ ТЕСТЫ
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `getRules returns empty config for non-existing project`() {
        val projectId = "new-project"

        whenever(ruleRepository.load(projectId)).thenReturn(null)

        mockMvc.get("/api/rules/{projectId}", projectId)
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.project_id", equalTo(projectId))
                    jsonPath("$.rules", equalTo(emptyList<Any>()))
                }
            }
    }

    @Test
    fun `saveRules rejects configuration with projectId mismatch`() {
        val pathProjectId = "path-id"
        val config = RulesConfig(projectId = "body-id", rules = emptyList())

        mockMvc.post("/api/rules/{projectId}", pathProjectId) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(config)
        }
            .andExpect {
                status { isBadRequest() }
                content {
                    jsonPath("$.error", equalTo("projectId mismatch"))
                }
            }
    }

    @Test
    fun `saveRules rejects invalid configuration`() {
        val projectId = "test-project"
        val config = RulesConfig(projectId = projectId, rules = emptyList())

        whenever(ruleRepository.validate(config)).thenReturn(
            ValidationResult.failure(listOf(Violation("test", "Invalid", "Test", severity = Severity.ERROR)))
        )

        mockMvc.post("/api/rules/{projectId}", projectId) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(config)
        }
            .andExpect {
                status { isBadRequest() }
                content {
                    jsonPath("$.error", equalTo("Invalid configuration"))
                }
            }
    }

    @Test
    fun `suggestRules returns suggestions based on project type`() {
        val projectId = "test-project"
        val projectType = "SPRING_BOOT"
        val mockRules = listOf(
            ArchitecturalRule(
                id = "rule_001",
                name = "Test rule",
                type = RuleType.DEPENDENCY,
                fromPackage = "..service..",
                toPackage = "..controller..",
                constraint = ConstraintType.NO_DEPENDENCY
            )
        )

        val mockStructure = ProjectStructure(
            projectId = projectId,
            packages = emptyList(),
            annotations = emptyMap()
        )
        whenever(projectScanner.scanProjectFromConfig(eq(projectId), any())).thenReturn(mockStructure)
        whenever(templateEngine.suggestRules(mockStructure)).thenReturn(mockRules)

        mockMvc.get("/api/rules/{projectId}/suggest?projectType={projectType}", projectId, projectType)
            .andExpect {
                status { isOk() }
                content {
                    // ✅ id остаётся id (не snake_case)
                    jsonPath("$[0].id", equalTo("rule_001"))
                    jsonPath("$[0].name", equalTo("Test rule"))
                }
            }
    }

    @Test
    fun `suggestRules returns empty list when project path not found`() {
        val projectId = "test-project"

        whenever(projectScanner.scanProjectFromConfig(eq(projectId), any())).thenReturn(null)

        mockMvc.get("/api/rules/{projectId}/suggest", projectId)
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$", equalTo(emptyList<Any>()))
                }
            }
    }

    @Test
    fun `suggestRules handles exception gracefully`() {
        val projectId = "test-project"

        whenever(projectScanner.scanProject(any(), eq(projectId))).thenThrow(RuntimeException("Scan failed"))

        mockMvc.get("/api/rules/{projectId}/suggest?projectPath=/invalid", projectId)
            .andExpect {
                status { isInternalServerError() }
                content {
                    jsonPath("$", equalTo(emptyList<Any>()))
                }
            }
    }

    @Test
    fun `deleteRules succeeds for existing project`() {
        val projectId = "test-project"

        whenever(ruleRepository.delete(projectId)).thenReturn(true)

        mockMvc.delete("/api/rules/{projectId}", projectId)
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.success", equalTo(true))
                }
            }
    }

    @Test
    fun `deleteRules fails for non-existing project`() {
        val projectId = "test-project"

        whenever(ruleRepository.delete(projectId)).thenReturn(false)

        mockMvc.delete("/api/rules/{projectId}", projectId)
            .andExpect {
                status { isBadRequest() }
                content {
                    jsonPath("$.error", equalTo("Failed to delete configuration"))
                }
            }
    }

    @Test
    fun `saveProjectPath updates config correctly`() {
        val projectId = "test-project"
        val projectPath = "/path/to/project"
        val existingConfig = RulesConfig(projectId = projectId, rules = emptyList())

        whenever(ruleRepository.load(projectId)).thenReturn(existingConfig)
        whenever(ruleRepository.save(any())).thenReturn(true)

        mockMvc.post("/api/rules/{projectId}/path", projectId) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"projectPath": "$projectPath"}"""
        }
            .andExpect {
                status { isOk() }
                content {
                    jsonPath("$.success", equalTo(true))
                    jsonPath("$.projectPath", equalTo(projectPath))
                }
            }
    }
}