package br.com.itau.notafiscal.domain.model

import java.math.BigDecimal

data class ItemTributado(
    val item: Item,
    val valorTributo: BigDecimal,
)

data class Tributacao(
    val aliquota: BigDecimal,
    val itens: List<ItemTributado>,
) {
    val totalTributos: BigDecimal = itens.sumOf { it.valorTributo }.comEscalaMonetaria()
}
