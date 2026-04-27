package com.example.archassistant.integration

import com.example.archassistant.config.YamlConfig
import com.example.archassistant.model.*
import com.example.archassistant.service.*
import com.example.archassistant.util.CodeCompiler
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.nio.file.Path

/**
 * Интеграционный тест после Этапа 7:
 * Проверяет полный пайплайн: YAML config → правила → LLM генерация → валидация
 *
 * Использует мок-ChatClient для симуляции LLM без реальных вызовов к API.
 */
@SpringBootTest
@Import(YamlConfig::class)  // YAML маппер из конфигурации
@ActiveProfiles("test")     // Активируем тестовый профиль
@ExtendWith(MockitoExtension::class)
class IntegrationAfterStage7Test {

    @TempDir
    lateinit var tempDir: Path

    @Autowired
    private lateinit var yamlMapper: ObjectMapper

    @Mock
    private lateinit var chatClient: ChatClient

    @Mock
    private lateinit var requestSpec: ChatClient.ChatClientRequestSpec

    @Mock
    private lateinit var responseSpec: ChatClient.CallResponseSpec

    @Captor
    private lateinit var systemPromptCaptor: ArgumentCaptor<String>

    @Captor
    private lateinit var userPromptCaptor: ArgumentCaptor<String>

    private lateinit var ruleRepository: YamlRuleRepository
    private lateinit var validator: DynamicRuleValidator
    private lateinit var orchestrator: LlmOrchestrator
    private lateinit var compiler: CodeCompiler

    @BeforeEach
    fun setUp() {
        // Репозиторий правил во временной директории
        ruleRepository = YamlRuleRepository(yamlMapper, tempDir.toString())

        // Компилятор и валидатор
        compiler = CodeCompiler()
        validator = DynamicRuleValidator(compiler)

        // Оркестратор с мок-чат-клиентом
        orchestrator = LlmOrchestrator(chatClient, ruleRepository)
    }

    /**
     * Формирует classpath, включающий зависимости и скомпилированные классы
     */
    private fun buildClasspath(vararg extraPaths: String): String {
        val cp = System.getProperty("java.class.path")
        val paths = cp.split(File.pathSeparator).toMutableList()
        paths.addAll(extraPaths)
        return paths.joinToString(File.pathSeparator)
    }

    /**
     * Настройка мока ChatClient для возврата заданного кода
     */
    private fun mockLlmResponse(responseCode: String) {
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(systemPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.user(userPromptCaptor.capture())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn(responseCode)
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `full pipeline = yaml config + llm generation + validation passes for valid code`() {
        // 1. Создаём YAML конфиг с правилами
        val config = RulesConfig(
            projectId = "integration.test",
            projectType = ProjectType.SPRING_BOOT,
            rules = listOf(
                ArchitecturalRule(
                    id = "no_controller_dep",
                    name = "Services should not depend on controllers",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.CRITICAL,
                    weight = 2.0,
                    enabled = true
                ),
                ArchitecturalRule(
                    id = "service_suffix",
                    name = "Services should have Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    severity = Severity.INFO,
                    weight = 0.5,
                    enabled = true
                )
            )
        )
        assertTrue(ruleRepository.save(config))

        // 2. Компилируем UserController для classpath
        val controllerCode = """
            package com.example.controller;
            public class UserController {
                public void handle() { }
            }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val controllerClassesDir = controllerRoot.resolve("classes").toString()
        val fullClasspath = buildClasspath(controllerClassesDir)

        // 3. Мок-ответ LLM: валидный код (без зависимости от контроллера)
        val validGeneratedCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                public void createUser(String name) {
                    // business logic here
                }
            }
        """.trimIndent()
        mockLlmResponse(validGeneratedCode)

        // 4. Запускаем генерацию через оркестратор
        val generationResponse = orchestrator.generateCode(
            prompt = "Create UserService for User entity",
            projectId = "integration.test",
            codeContext = null
        )

        // 5. Проверяем, что генерация успешна
        assertTrue(generationResponse.success, "Generation should succeed")
        assertNotNull(generationResponse.data?.code, "Generated code should not be null")
        assertTrue(generationResponse.metadata.generationTimeMs > 0, "Generation time should be recorded")

        // 6. Проверяем, что правила попали в system prompt
        val systemPrompt = systemPromptCaptor.value
        assertTrue(systemPrompt.contains("Services should not depend on controllers"))
        assertTrue(systemPrompt.contains("[ОБЯЗАТЕЛЬНО]"))
        assertTrue(systemPrompt.contains("Возвращай ТОЛЬКО код"))

        // 7. Валидируем сгенерированный код против правил
        val rules = ruleRepository.load("integration.test")!!.getEnabledRules()
        val validationResult = validator.validate(
            code = generationResponse.data!!.code,
            className = "UserService",
            rules = rules,
            classpath = fullClasspath
        )

        // 8. Валидный код должен пройти валидацию
        assertTrue(validationResult.passed, "Valid code should pass validation: ${validationResult.message}")
        assertTrue(validationResult.violations.isEmpty(), "No violations expected for valid code")

        compiler.cleanup(controllerRoot)
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `full pipeline = generated code with dependency violation fails validation`() {
        // 1. Создаём конфиг с правилом запрета зависимостей
        val config = RulesConfig(
            projectId = "violation.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "no_controller_dep",
                    name = "Services should not depend on controllers",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    severity = Severity.CRITICAL,
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        // 2. Компилируем UserController для classpath
        val controllerCode = """
            package com.example.controller;
                public class UserController {
                    public void handle() { }
                }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val classpath = buildClasspath(controllerRoot.resolve("classes").toString())

        // 3. Мок-ответ LLM: код с нарушением (зависит от UserController)
        val invalidGeneratedCode = """
            package com.example.service;
            import com.example.controller.UserController;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                private UserController controller;
                
                public void handle() {
                    controller.handle();
                }
            }
        """.trimIndent()
        mockLlmResponse(invalidGeneratedCode)

        // 4. Запускаем генерацию
        val generationResponse = orchestrator.generateCode(
            prompt = "Create UserService that uses UserController",
            projectId = "violation.test"
        )

        assertTrue(generationResponse.success)

        // 5. Валидируем сгенерированный код
        val rules = ruleRepository.load("violation.test")!!.getEnabledRules()
        val validationResult = validator.validate(
            code = generationResponse.data!!.code,
            className = "UserService",
            rules = rules,
            classpath = classpath
        )

        // 6. Код с нарушением должен провалить валидацию
        assertFalse(validationResult.passed, "Code with dependency violation should fail")
        assertTrue(
            validationResult.violations.any { it.ruleId == "no_controller_dep" },
            "Should report no_controller_dep violation, actual: ${validationResult.violations.map { it.ruleId }}"
        )
        assertEquals(Severity.CRITICAL, validationResult.violations.first().severity)

        compiler.cleanup(controllerRoot)
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `full pipeline = naming convention violation is detected`() {
        // 1. Конфиг с правилом именования
        val config = RulesConfig(
            projectId = "naming.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "service_suffix",
                    name = "Services should have Service suffix",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    severity = Severity.INFO,
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        // 2. Мок-ответ: код с неправильным именем класса
        val invalidNamingCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserLogic {
                public void process() { }
            }
        """.trimIndent()
        mockLlmResponse(invalidNamingCode)

        // 3. Генерация
        val response = orchestrator.generateCode(
            prompt = "Create service for user processing",
            projectId = "naming.test"
        )

        assertTrue(response.success)

        // 4. Валидация
        val rules = ruleRepository.load("naming.test")!!.getEnabledRules()
        val result = validator.validate(response.data!!.code, "UserLogic", rules)

        assertFalse(result.passed)
        assertTrue(
            result.violations.any { it.ruleId == "service_suffix" },
            "Should report service_suffix violation"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `disabled rules are ignored during generation and validation`() {
        // 1. Конфиг с отключённым правилом
        val config = RulesConfig(
            projectId = "disabled.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "disabled_dep",
                    name = "Disabled dependency rule",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    enabled = false  // Отключено!
                )
            )
        )
        ruleRepository.save(config)

        // 2. Компилируем контроллер
        val controllerCode = "package com.example.controller; public class UserController { }"
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val classpath = controllerRoot.resolve("classes").toString()

        // 3. Мок-ответ: код с зависимостью (которая запрещена, но правило отключено)
        val codeWithDep = """
            package com.example.service;
            import com.example.controller.UserController;
            public class UserService {
                private UserController ctrl;
            }
        """.trimIndent()
        mockLlmResponse(codeWithDep)

        // 4. Генерация
        val response = orchestrator.generateCode(
            prompt = "Create service",
            projectId = "disabled.test"
        )

        assertTrue(response.success)

        // 5. Валидация: отключённые правила не должны проверяться
        val rules = ruleRepository.load("disabled.test")!!.getEnabledRules()
        assertTrue(rules.isEmpty(), "Disabled rules should not be loaded as enabled")

        val result = validator.validate(response.data!!.code, "UserService", rules, classpath)
        assertTrue(result.passed, "Disabled rules should be ignored during validation")

        compiler.cleanup(controllerRoot)
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `context is included in user prompt, not system prompt`() {
        // 1. Простой конфиг
        val config = RulesConfig(
            projectId = "context.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "simple",
                    name = "Simple rule",
                    type = RuleType.NAMING_CONVENTION,
                    fromPackage = "..service..",
                    constraint = ConstraintType.NAMING_SUFFIX,
                    pattern = "Service",
                    enabled = true
                )
            )
        )
        ruleRepository.save(config)

        // 2. Мок с любым ответом
        mockLlmResponse("package test; public class TestService { }")

        // 3. Генерация с контекстом
        val codeContext = "public class ExampleService { public void example() { } }"
        orchestrator.generateCode(
            prompt = "Create similar service",
            projectId = "context.test",
            codeContext = codeContext
        )

        // 4. Проверяем, что контекст в user prompt, а не в system prompt
        val systemPrompt = systemPromptCaptor.value
        val userPrompt = userPromptCaptor.value

        assertFalse(systemPrompt.contains(codeContext), "Context should NOT be in system prompt")
        assertTrue(userPrompt.contains(codeContext), "Context SHOULD be in user prompt")
        assertTrue(userPrompt.contains("ПРИМЕРЫ ИЗ ПРОЕКТА"), "User prompt should have context section header")
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `empty prompt returns error without calling LLM`() {
        // Генерация с пустым промптом
        val response = orchestrator.generateCode(
            prompt = "",
            projectId = "empty.test"
        )

        // Должна вернуться ошибка без вызова LLM
        assertFalse(response.success)
        assertEquals("INVALID_PROMPT", response.error?.code)
        verify(chatClient, never()).prompt()  // LLM не должен вызываться
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `llm error is handled gracefully`() {
        // 1. Настраиваем мок на выброс исключения
        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenThrow(RuntimeException("LLM service unavailable"))

        // 2. Запускаем генерацию
        val response = orchestrator.generateCode(
            prompt = "Test",
            projectId = "error.test",
            maxRetries = 1  // Минимум ретраев для скорости теста
        )

        // 3. Проверяем обработку ошибки
        assertFalse(response.success)
        assertEquals("LLM_ERROR", response.error?.code)
        assertNotNull(response.error?.message)
    }
}