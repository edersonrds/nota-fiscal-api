package br.com.itau.notafiscal.domain.model

import java.math.BigDecimal

data class Item(
    val idItem: String,
    val descricao: String,
    val quantidade: Int,
    val valorUnitario: BigDecimal,
) {
    init {
        require(idItem.isNotBlank()) { "id_item é obrigatório" }
        require(quantidade > 0) { "quantidade deve ser maior que zero" }
        require(valorUnitario.signum() > 0) { "valor_unitario deve ser maior que zero" }
        require(valorUnitario.temNoMaximoDuasCasas()) { "valor_unitario deve ter no máximo 2 casas decimais" }
    }

    val valorTotal: BigDecimal = (valorUnitario * quantidade.toBigDecimal()).comEscalaMonetaria()
}
