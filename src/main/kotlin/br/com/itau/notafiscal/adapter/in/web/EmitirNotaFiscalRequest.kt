package br.com.itau.notafiscal.adapter.`in`.web

import br.com.itau.notafiscal.domain.model.Finalidade
import br.com.itau.notafiscal.domain.model.Regiao
import br.com.itau.notafiscal.domain.model.RegimeTributario
import java.math.BigDecimal
import java.time.LocalDate

data class EmitirNotaFiscalRequest(
    val idPedido: String,
    val data: LocalDate,
    val cliente: ClienteRequest,
    val itens: List<ItemRequest>,
    val valorFrete: BigDecimal,
)

data class ClienteRequest(
    val tipoPessoa: TipoPessoa,
    val documento: String,
    val nome: String,
    val regimeTributacao: RegimeTributario? = null,
    val enderecos: List<EnderecoRequest>,
)

data class EnderecoRequest(
    val finalidade: Finalidade,
    val regiao: Regiao,
    val logradouro: String,
    val numero: String,
    val cidade: String,
    val estado: String,
    val cep: String,
)

data class ItemRequest(
    val idItem: String,
    val descricao: String,
    val quantidade: Int,
    val valorUnitario: BigDecimal,
)

enum class TipoPessoa {
    FISICA,
    JURIDICA,
}
