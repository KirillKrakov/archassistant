package com.example.archassistant

import com.example.archassistant.service.YamlRuleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.nio.file.Files

@TestConfiguration
class TestYamlConfig {

    @Bean
    @Primary
    fun testYamlRuleRepository(yamlObjectMapper: ObjectMapper): YamlRuleRepository {
        val tempDir = Files.createTempDirectory("archassistant-test-config")
        tempDir.toFile().deleteOnExit()  // Очистка после тестов
        return YamlRuleRepository(yamlObjectMapper, tempDir.toString())
    }
}