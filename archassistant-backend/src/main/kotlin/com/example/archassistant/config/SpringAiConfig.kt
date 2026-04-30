package com.example.archassistant.config

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringAiConfig {

    private val logger = LoggerFactory.getLogger(SpringAiConfig::class.java)

    @Bean
    fun openAiApi(): OpenAiApi {
        val apiKey = System.getenv("GROQ_API_KEY")
            ?: System.getenv("OPENAI_API_KEY")
            ?: throw IllegalStateException("No API key found. Set GROQ_API_KEY or OPENAI_API_KEY.")

        val baseUrl = System.getenv("GROQ_BASE_URL") ?: "https://api.groq.com/openai"

        logger.info("Creating OpenAiApi with baseUrl: $baseUrl")

        return OpenAiApi(baseUrl, apiKey)
    }

    @Bean
    fun openAiChatOptions(): OpenAiChatOptions {
        val model = System.getenv("GROQ_MODEL") ?: "llama-3.3-70b-versatile"
        return OpenAiChatOptions.builder()
            .withModel(model)
            .withTemperature(0.2)
            .withMaxTokens(4096)
            .withFrequencyPenalty(0.0)
            .withPresencePenalty(0.0)
            .build()
    }

    @Bean
    fun chatClient(openAiApi: OpenAiApi, openAiChatOptions: OpenAiChatOptions): ChatClient {
        val chatModel = OpenAiChatModel(openAiApi, openAiChatOptions)
        return ChatClient.builder(chatModel).build()
    }
}