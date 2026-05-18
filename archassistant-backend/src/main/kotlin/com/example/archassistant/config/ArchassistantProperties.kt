package com.example.archassistant.config

import com.example.archassistant.dto.metrics.request.ExportFormat
import com.example.archassistant.model.core.ScoreWeights
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "archassistant")
data class ArchassistantProperties(
    val configRoot: String = ".archassistant",
    val compliance: Compliance = Compliance(),
    val export: Export = Export(),
    val cache: Cache = Cache(),
    val llm: Llm = Llm()
) {
    data class Compliance(
        val threshold: Double = 70.0,
        val weights: ScoreWeights = ScoreWeights()
    )

    data class Export(
        val maxRecords: Int = 10_000,
        val defaultFormat: ExportFormat = ExportFormat.CSV
    )

    data class Cache(
        val projectContextTtlMinutes: Long = 30,
        val maxEntries: Int = 32
    )

    data class Llm(
        val provider: String = "mistral",
        val mistral: Mistral = Mistral(),
        val gigachat: GigaChat = GigaChat()
    ) {
        data class Mistral(
            val baseUrl: String = "https://api.mistral.ai",
            val apiKey: String = "",
            val model: String = "mistral-medium-latest",
            val temperature: Double = 0.2,
            val maxTokens: Int = 10_000
        )

        data class GigaChat(
            val baseUrl: String = "https://gigachat.devices.sberbank.ru/api/v1",
            val apiKey: String = "",
            val scope: String = "GIGACHAT_API_PERS",
            val model: String = "GigaChat",
            val temperature: Double = 0.2,
            val maxTokens: Int = 10_000,
            val connectTimeout: String = "15s",
            val readTimeout: String = "30s",
            val unsafeSsl: Boolean = false
        )
    }
}