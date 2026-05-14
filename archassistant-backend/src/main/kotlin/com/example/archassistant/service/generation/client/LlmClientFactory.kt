package com.example.archassistant.service.generation.client

import chat.giga.springai.GigaChatModel
import com.example.archassistant.config.ArchassistantProperties
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

@Component
class LlmClientFactory(
    private val properties: ArchassistantProperties,
    private val gigaChatModelProvider: ObjectProvider<GigaChatModel>
) {

    fun currentProviderName(): String = normalizedProvider()

    fun currentModelName(): String {
        return when (normalizedProvider()) {
            "gigachat" -> properties.llm.gigachat.model
            else -> properties.llm.mistral.model
        }
    }

    fun createChatClient(): ChatClient {
        return when (normalizedProvider()) {
            "gigachat" -> createGigaChatClient()
            "mistral" -> createMistralChatClient()
            else -> throw IllegalStateException(
                "Unsupported LLM provider '${properties.llm.provider}'. " +
                        "Supported providers: mistral, gigachat."
            )
        }
    }

    private fun normalizedProvider(): String = properties.llm.provider.trim().lowercase()

    private fun createMistralChatClient(): ChatClient {
        val provider = properties.llm.mistral

        require(provider.apiKey.isNotBlank()) {
            "No API key configured for Mistral."
        }
        require(provider.baseUrl.isNotBlank()) {
            "No base URL configured for Mistral."
        }
        require(provider.model.isNotBlank()) {
            "No model configured for Mistral."
        }

        val api = OpenAiApi.builder()
            .baseUrl(provider.baseUrl)
            .apiKey(provider.apiKey)
            .build()

        val options = OpenAiChatOptions.builder()
            .model(provider.model)
            .temperature(provider.temperature)
            .maxTokens(provider.maxTokens)
            .frequencyPenalty(0.0)
            .presencePenalty(0.0)
            .build()

        val chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build()

        return ChatClient.builder(chatModel).build()
    }

    private fun createGigaChatClient(): ChatClient {
        val provider = properties.llm.gigachat

        require(provider.apiKey.isNotBlank()) {
            "No API key configured for GigaChat."
        }

        val gigaChatModel = gigaChatModelProvider.getIfAvailable()
            ?: throw IllegalStateException(
                "GigaChatModel bean is not available. Check spring.ai.gigachat autoconfiguration and properties."
            )

        return ChatClient.builder(gigaChatModel).build()
    }
}