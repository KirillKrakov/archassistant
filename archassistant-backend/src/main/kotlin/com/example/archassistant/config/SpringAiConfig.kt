package com.example.archassistant.config

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ArchassistantProperties::class)
class SpringAiConfig {

    private val logger = LoggerFactory.getLogger(SpringAiConfig::class.java)

    @Bean
    fun openAiApi(properties: ArchassistantProperties): OpenAiApi {
        val provider = properties.activeProvider()

        require(provider.apiKey.isNotBlank()) {
            "No API key found for provider='${properties.llm.provider}'. Set the corresponding env variable."
        }

        logger.info(
            "Creating OpenAiApi for provider={} with baseUrl={}",
            properties.llm.provider,
            provider.baseUrl
        )

        return OpenAiApi(provider.baseUrl, provider.apiKey)
    }

    @Bean
    fun openAiChatOptions(properties: ArchassistantProperties): OpenAiChatOptions {
        val provider = properties.activeProvider()

        return OpenAiChatOptions.builder()
            .withModel(provider.model.ifBlank { null })
            .withTemperature(provider.temperature)
            .withMaxTokens(provider.maxTokens)
            .withFrequencyPenalty(0.0)
            .withPresencePenalty(0.0)
            .build()
    }

    @Bean
    fun chatClient(
        openAiApi: OpenAiApi,
        openAiChatOptions: OpenAiChatOptions
    ): ChatClient {
        val chatModel = OpenAiChatModel(openAiApi, openAiChatOptions)
        return ChatClient.builder(chatModel).build()
    }
}