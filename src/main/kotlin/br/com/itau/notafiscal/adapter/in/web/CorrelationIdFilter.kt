package br.com.itau.notafiscal.adapter.`in`.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId =
            request.getHeader(CABECALHO)?.takeIf { FORMATO_VALIDO.matches(it) } ?: UUID.randomUUID().toString()
        MDC.put(CHAVE_MDC, correlationId)
        response.setHeader(CABECALHO, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(CHAVE_MDC)
        }
    }

    companion object {
        const val CABECALHO = "X-Correlation-Id"
        const val CHAVE_MDC = "correlationId"
        private val FORMATO_VALIDO = Regex("[A-Za-z0-9-]{1,64}")
    }
}
