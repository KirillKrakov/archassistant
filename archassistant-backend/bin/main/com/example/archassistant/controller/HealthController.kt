package com.example.archassistant.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                version = "0.0.1-SNAPSHOT",
                timestamp = LocalDateTime.now()
            )
        )
    }

    data class HealthResponse(
        val status: String,
        val version: String,
        val timestamp: LocalDateTime
    )
}