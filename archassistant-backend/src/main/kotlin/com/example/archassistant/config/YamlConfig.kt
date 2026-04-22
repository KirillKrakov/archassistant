package com.example.archassistant.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Конфигурация Jackson для работы с YAML
 */
@Configuration
class YamlConfig {

    @Bean(name = ["yamlObjectMapper"])
    fun yamlObjectMapper(): ObjectMapper {
        return ObjectMapper(
            YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        ).registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .build()
        )
    }
}