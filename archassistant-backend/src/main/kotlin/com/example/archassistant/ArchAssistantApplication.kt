package com.example.archassistant

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ArchAssistantApplication

fun main(args: Array<String>) {
    runApplication<ArchAssistantApplication>(*args)
}