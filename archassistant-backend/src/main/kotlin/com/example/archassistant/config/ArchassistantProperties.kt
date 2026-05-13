package com.example.archassistant.config

import com.example.archassistant.dto.ExportFormat
import com.example.archassistant.model.ScoreWeights
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
        val mistral: Provider = Provider(
            baseUrl = "https://api.mistral.ai",
            model = "mistral-medium-latest"
        ),
        val deepseek: Provider = Provider(
            baseUrl = "https://api.deepseek.com",
            model = "deepseek-chat"
        ),
        val groq: Provider = Provider(
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile"
        )
    ) {
        data class Provider(
            val baseUrl: String,
            val apiKey: String = "",
            val model: String = "",
            val temperature: Double = 0.2,
            val maxTokens: Int = 10_000
        )
    }

    fun activeProvider(): Llm.Provider {
        return when (llm.provider.trim().lowercase()) {
            "deepseek" -> llm.deepseek
            "groq" -> llm.groq
            else -> llm.mistral
        }
    }
}