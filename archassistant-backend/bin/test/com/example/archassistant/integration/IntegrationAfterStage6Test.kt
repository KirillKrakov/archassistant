package com.example.archassistant.integration

import com.example.archassistant.model.*
import com.example.archassistant.service.*
import com.example.archassistant.util.CodeCompiler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class IntegrationAfterStage6Test {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var yamlMapper: ObjectMapper
    private lateinit var ruleRepository: YamlRuleRepository
    private lateinit var validator: DynamicRuleValidator
    private lateinit var compiler: CodeCompiler

    @BeforeEach
    fun setUp() {
        yamlMapper = ObjectMapper(YAMLFactory())
            .registerModule(KotlinModule.Builder().build())
        ruleRepository = YamlRuleRepository(yamlMapper, tempDir.toString())
        compiler = CodeCompiler()
        validator = DynamicRuleValidator(compiler)
    }

    /**
     * Формирует classpath, включающий:
     * - Все зависимости из classpath текущего процесса (JVM)
     * - Директорию со скомпилированными классами UserController
     */
    private fun buildClasspath(vararg extraPaths: String): String {
        val cp = System.getProperty("java.class.path")
        val paths = cp.split(File.pathSeparator).toMutableList()
        paths.addAll(extraPaths)
        return paths.joinToString(File.pathSeparator)
    }

    @Test
    fun `full pipeline = yaml config + rules + validation with full classpath`() {
        // 1. Создаём YAML конфиг
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
        val loadedConfig = ruleRepository.load("integration.test")!!
        val rules = loadedConfig.getEnabledRules()

        // 2. Компилируем UserController (временная папка)
        val controllerCode = """
            package com.example.controller;
            public class UserController {
                public void handle() { }
            }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val controllerClassesDir = controllerRoot.resolve("classes").toString()

        // 3. Полный classpath (все зависимости теста + наши скомпилированные классы)
        val fullClasspath = buildClasspath(controllerClassesDir)

        // 4. Валидный код (без зависимости) — должен пройти
        val validCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                public void createUser(String name) { }
            }
        """.trimIndent()
        val validResult = validator.validate(validCode, "UserService", rules, fullClasspath)
        assertTrue(validResult.passed, "Valid code should pass: ${validResult.message}")

        // 5. Код с нарушением зависимости (зависит от UserController) — должен провалиться
        val invalidCode = """
            package com.example.service;
            import com.example.controller.UserController;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                private UserController controller;
            }
        """.trimIndent()
        val invalidResult = validator.validate(invalidCode, "UserService", rules, fullClasspath)
        assertFalse(invalidResult.passed, "Code with dependency violation should fail")
        assertTrue(
            invalidResult.violations.any { it.ruleId == "no_controller_dep" },
            "Should report no_controller_dep violation, actual: ${invalidResult.violations}"
        )

        // 6. Код с неправильным именованием — должен провалиться
        val invalidNamingCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserLogic { }
        """.trimIndent()
        val namingResult = validator.validate(invalidNamingCode, "UserLogic", rules, fullClasspath)
        assertFalse(namingResult.passed, "Code with naming violation should fail")
        assertTrue(
            namingResult.violations.any { it.ruleId == "service_suffix" },
            "Should report service_suffix violation"
        )

        compiler.cleanup(controllerRoot)
    }

    @Test
    fun `disabled rules are ignored in validation`() {
        val config = RulesConfig(
            projectId = "disabled.test",
            rules = listOf(
                ArchitecturalRule(
                    id = "disabled_rule",
                    name = "Disabled",
                    type = RuleType.DEPENDENCY,
                    fromPackage = "..service..",
                    toPackage = "..controller..",
                    constraint = ConstraintType.NO_DEPENDENCY,
                    enabled = false
                )
            )
        )
        ruleRepository.save(config)
        val loaded = ruleRepository.load("disabled.test")!!
        val rules = loaded.getEnabledRules() // пустой список

        // Компилируем контроллер для classpath (можно без него, но для единообразия)
        val controllerCode = """
            package com.example.controller;
            public class UserController { }
        """.trimIndent()
        val controllerRoot = compiler.compileCode(controllerCode, "UserController")
        val classpath = controllerRoot.resolve("classes").toString()

        // Код с зависимостью, но правило отключено — должен пройти
        val code = """
            package com.example.service;
            import com.example.controller.UserController;
            public class UserService {
                private UserController ctrl;
            }
        """.trimIndent()
        val result = validator.validate(code, "UserService", rules, classpath)
        assertTrue(result.passed, "Disabled rules should be ignored, but got ${result.message}")
        compiler.cleanup(controllerRoot)
    }
}