package br.com.itau.notafiscal.adapter.`in`.web

import br.com.itau.notafiscal.domain.model.Regiao
import br.com.itau.notafiscal.domain.model.RegimeTributario
import java.math.BigDecimal
import java.time.OffsetDateTime

data class NotaFiscalResponse(
    val numeroNota: Long,
    val idPedido: String,
    val dataEmissao: OffsetDateTime,
    val cliente: ClienteResponse,
    val aliquotaPercentual: BigDecimal,
    val itens: List<ItemResponse>,
    val frete: FreteResponse,
    val totalItens: BigDecimal,
    val totalTributos: BigDecimal,
    val totalNota: BigDecimal,
)

data class ClienteResponse(
    val tipoPessoa: TipoPessoa,
    val documento: String,
    val nome: String,
    val regimeTributacao: RegimeTributario?,
)

data class ItemResponse(
    val idItem: String,
    val descricao: String,
    val quantidade: Int,
    val valorUnitario: BigDecimal,
    val valorTotal: BigDecimal,
    val valorTributo: BigDecimal,
)

data class FreteResponse(
    val regiao: Regiao,
    val valorBase: BigDecimal,
    val percentual: BigDecimal,
    val valor: BigDecimal,
)
