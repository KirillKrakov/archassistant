package com.example.archassistant.controller

import com.example.archassistant.dto.health.ApplicationHealthResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    fun health(): ResponseEntity<ApplicationHealthResponse> {
        return ResponseEntity.ok(
            ApplicationHealthResponse(
                status = "UP",
                version = "0.0.1-SNAPSHOT",
                timestamp = LocalDateTime.now().toString()
            )
        )
    }
}