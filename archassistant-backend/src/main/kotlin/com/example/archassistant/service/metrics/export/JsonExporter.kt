package com.example.archassistant.service.metrics.export

import com.example.archassistant.entity.GenerationRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.stereotype.Component

@Component
class JsonExporter(
    private val objectMapper: ObjectMapper
) {
    private val exportMapper: ObjectMapper = objectMapper.copy()
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule.Builder().build())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun export(records: List<GenerationRecord>, pretty: Boolean): String {
        return if (pretty) {
            exportMapper.writerWithDefaultPrettyPrinter().writeValueAsString(records)
        } else {
            exportMapper.writeValueAsString(records)
        }
    }
}