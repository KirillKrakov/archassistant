package com.example.archassistant.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringAiConfig {

    @Bean
    fun openAiApi(): OpenAiApi {
        // Метод .builder() отсутствует, экземпляр создается через конструктор.
        return OpenAiApi(
            System.getenv("OPENAI_API_KEY") ?: "demo", // API-ключ
            "https://api.openai.com/v1"               // Базовый URL
        )
    }

    @Bean
    fun openAiChatOptions(): OpenAiChatOptions {
        return OpenAiChatOptions.builder()
            // Все сеттеры теперь имеют префикс 'with'
            .withModel("gpt-3.5-turbo")
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