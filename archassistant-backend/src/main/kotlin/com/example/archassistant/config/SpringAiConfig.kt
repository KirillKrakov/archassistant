package com.example.archassistant.config

import com.example.archassistant.service.generation.client.LlmClientFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ArchassistantProperties::class)
class SpringAiConfig {

    @Bean
    fun chatClient(factory: LlmClientFactory): ChatClient {
        return factory.createChatClient()
    }
}