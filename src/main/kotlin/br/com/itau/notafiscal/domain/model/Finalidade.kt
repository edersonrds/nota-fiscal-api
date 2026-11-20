package br.com.itau.notafiscal.domain.model

enum class Finalidade(
    val defineEntrega: Boolean,
) {
    ENTREGA(true),
    COBRANCA_ENTREGA(true),
    COBRANCA(false),
}
