package br.com.itau.notafiscal.adapter.`in`.web

import br.com.itau.notafiscal.application.exception.PedidoJaEmitidoException
import br.com.itau.notafiscal.domain.exception.RegraNegocioException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tools.jackson.databind.exc.InvalidNullException
import tools.jackson.databind.exc.MismatchedInputException

data class CampoInvalido(
    val campo: String,
    val mensagem: String,
)

@RestControllerAdvice
class TratadorDeErros : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(RegraNegocioException::class)
    fun regraDeNegocio(erro: RegraNegocioException): ProblemDetail =
        problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regra de negócio violada", erro.message.orEmpty(), erro.codigo)

    @ExceptionHandler(PedidoJaEmitidoException::class)
    fun pedidoJaEmitido(erro: PedidoJaEmitidoException): ProblemDetail =
        problema(HttpStatus.CONFLICT, "Pedido já emitido", erro.message.orEmpty(), "PEDIDO_JA_EMITIDO")
            .apply { setProperty("numero_nota", erro.numeroNota) }

    @ExceptionHandler(Exception::class)
    fun erroInesperado(erro: Exception): ProblemDetail {
        log.error("Erro inesperado ao processar a requisição", erro)
        return problema(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro interno",
            "Ocorreu um erro inesperado. Tente novamente mais tarde.",
            "ERRO_INTERNO",
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val campos =
            ex.bindingResult.fieldErrors
                .map { CampoInvalido(it.field.emSnakeCase(), it.defaultMessage ?: "valor inválido") }
                .sortedBy { it.campo }
        return handleExceptionInternal(ex, requisicaoInvalida(CAMPOS_INVALIDOS, campos), headers, status, request)
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val erroDeCampo = generateSequence(ex.cause) { it.cause }.filterIsInstance<MismatchedInputException>().firstOrNull()
        val corpo =
            if (erroDeCampo == null) {
                requisicaoInvalida("O corpo da requisição não é um JSON válido.", emptyList())
            } else {
                requisicaoInvalida(CAMPOS_INVALIDOS, listOf(CampoInvalido(erroDeCampo.campo(), erroDeCampo.mensagem())))
            }
        return handleExceptionInternal(ex, corpo, headers, status, request)
    }

    private fun requisicaoInvalida(
        detalhe: String,
        campos: List<CampoInvalido>,
    ) = problema(HttpStatus.BAD_REQUEST, "Requisição inválida", detalhe, "REQUISICAO_INVALIDA")
        .apply { setProperty("campos", campos) }

    private fun problema(
        status: HttpStatus,
        titulo: String,
        detalhe: String,
        codigo: String,
    ) = ProblemDetail.forStatusAndDetail(status, detalhe).apply {
        title = titulo
        setProperty("codigo", codigo)
    }

    private fun MismatchedInputException.campo(): String =
        path
            .joinToString("") { if (it.index >= 0) "[${it.index}]" else ".${it.propertyName}" }
            .removePrefix(".")

    private fun MismatchedInputException.mensagem(): String {
        val tipo = targetType
        return when {
            this is InvalidNullException -> "é obrigatório"
            tipo != null && tipo.isEnum -> "valor inválido; aceitos: ${tipo.enumConstants.joinToString()}"
            else -> "tipo ou formato inválido"
        }
    }

    private fun String.emSnakeCase() = replace(Regex("([a-z0-9])([A-Z])"), "\$1_\$2").lowercase()

    private companion object {
        const val CAMPOS_INVALIDOS = "Um ou mais campos são inválidos."
    }
}
