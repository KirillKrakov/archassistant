package com.example.archassistant.util

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Фильтр для добавления correlation-id в каждый запрос
 * Позволяет отслеживать полный путь запроса в логах
 */
@Component
@Order(1)
class CorrelationIdFilter : OncePerRequestFilter() {

    companion object {
        private const val CORRELATION_ID_HEADER = "X-Correlation-Id"
        private const val CORRELATION_ID_MDC = "correlationId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = request.getHeader(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString().take(12)

        // Добавляем в MDC для логирования
        MDC.put(CORRELATION_ID_MDC, correlationId)

        // Добавляем в ответ для клиента
        response.setHeader(CORRELATION_ID_HEADER, correlationId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(CORRELATION_ID_MDC)
        }
    }
}