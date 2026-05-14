package com.example.archassistant.service.metrics.export

import com.example.archassistant.model.GenerationRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.stereotype.Component

@Component
class JsonExporter(
    private val objectMapper: ObjectMapper
) {

    init {
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.registerModule(KotlinModule.Builder().build())
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    /**
     * Экспорт записей в JSON-формат
     */
    fun export(records: List<GenerationRecord>, pretty: Boolean): String {
        return if (pretty) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(records)
        } else {
            objectMapper.writeValueAsString(records)
        }
    }
}