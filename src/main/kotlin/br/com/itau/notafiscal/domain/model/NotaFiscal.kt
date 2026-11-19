package br.com.itau.notafiscal.domain.model

import java.math.BigDecimal
import java.time.OffsetDateTime

data class NotaFiscal(
    val numero: Long,
    val dataEmissao: OffsetDateTime,
    val pedido: Pedido,
    val tributacao: Tributacao,
    val frete: Frete,
) {
    init {
        require(numero > 0) { "número da nota deve ser positivo" }
        require(tributacao.itens.map { it.item } == pedido.itens) { "tributação não corresponde aos itens do pedido" }
        require(frete.valorBase.compareTo(pedido.valorFrete) == 0) { "frete não corresponde ao valor de frete do pedido" }
    }

    val totalItens: BigDecimal = pedido.totalItens
    val totalTributos: BigDecimal = tributacao.totalTributos
    val totalNota: BigDecimal = totalItens + totalTributos + frete.valor
}
