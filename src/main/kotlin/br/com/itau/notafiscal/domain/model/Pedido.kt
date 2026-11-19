package br.com.itau.notafiscal.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Pedido(
    val idPedido: String,
    val data: LocalDate,
    val cliente: Cliente,
    val itens: List<Item>,
    val valorFrete: BigDecimal,
) {
    init {
        require(idPedido.isNotBlank()) { "id_pedido é obrigatório" }
        require(itens.isNotEmpty()) { "pedido deve ter ao menos um item" }
        require(valorFrete.signum() >= 0) { "valor_frete não pode ser negativo" }
        require(valorFrete.temNoMaximoDuasCasas()) { "valor_frete deve ter no máximo 2 casas decimais" }
    }

    val totalItens: BigDecimal = itens.sumOf { it.valorTotal }.comEscalaMonetaria()
}
