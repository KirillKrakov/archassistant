package com.example.archassistant.controller.advice

import com.example.archassistant.dto.ErrorDetails
import com.example.archassistant.dto.ResponseMetadata
import com.example.archassistant.dto.CodeGenerationResponse
import com.example.archassistant.service.LlmGenerationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<CodeGenerationResponse> {
        return buildError(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_ARGUMENT",
            message = exception.message ?: "Invalid request"
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(exception: IllegalStateException): ResponseEntity<CodeGenerationResponse> {
        return buildError(
            status = HttpStatus.CONFLICT,
            code = "ILLEGAL_STATE",
            message = exception.message ?: "Illegal state"
        )
    }

    @ExceptionHandler(LlmGenerationException::class)
    fun handleLlmGeneration(exception: LlmGenerationException): ResponseEntity<CodeGenerationResponse> {
        logger.warn("LLM generation error: {}", exception.message)
        return buildError(
            status = HttpStatus.BAD_GATEWAY,
            code = "LLM_ERROR",
            message = exception.message ?: "LLM generation failed"
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<CodeGenerationResponse> {
        logger.error("Unhandled exception", exception)
        return buildError(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_ERROR",
            message = "Internal server error"
        )
    }

    private fun buildError(
        status: HttpStatus,
        code: String,
        message: String
    ): ResponseEntity<CodeGenerationResponse> {
        val response = CodeGenerationResponse(
            success = false,
            data = null,
            error = ErrorDetails(code = code, message = message),
            metadata = ResponseMetadata(
                generationTimeMs = 0,
                validationTimeMs = 0,
                totalTimeMs = 0,
                timestamp = LocalDateTime.now().toString()
            )
        )
        return ResponseEntity.status(status).body(response)
    }
}