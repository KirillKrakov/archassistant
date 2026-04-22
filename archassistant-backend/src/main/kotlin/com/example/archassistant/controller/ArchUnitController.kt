package com.example.archassistant.controller

import com.example.archassistant.dto.ValidationRequest
import com.example.archassistant.dto.ValidationResponse
import com.example.archassistant.service.ArchUnitValidator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/archunit")
class ArchUnitController(private val validator: ArchUnitValidator) {

    @PostMapping("/validate")
    fun validateCode(@RequestBody request: ValidationRequest): ResponseEntity<ValidationResponse> {
        val result = if (request.className != null) {
            validator.validate(request.code, request.className)
        } else {
            validator.validate(request.code)
        }
        return ResponseEntity.ok(ValidationResponse(result))
    }

    @PostMapping("/validate/basic")
    fun validateBasic(@RequestBody request: ValidationRequest): ResponseEntity<ValidationResponse> {
        val result = if (request.className != null) {
            validator.validateBasic(request.code, request.className)
        } else {
            validator.validateBasic(request.code)
        }
        return ResponseEntity.ok(ValidationResponse(result))
    }
}