package br.com.itau.notafiscal.adapter.`in`.web

import br.com.itau.notafiscal.adapter.`in`.web.validacao.DocumentoCompativel
import br.com.itau.notafiscal.adapter.`in`.web.validacao.DuasCasasDecimais
import br.com.itau.notafiscal.domain.model.Finalidade
import br.com.itau.notafiscal.domain.model.Regiao
import br.com.itau.notafiscal.domain.model.RegimeTributario
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDate

data class EmitirNotaFiscalRequest(
    @field:NotBlank(message = "é obrigatório")
    val idPedido: String,
    val data: LocalDate,
    @field:Valid
    val cliente: ClienteRequest,
    @field:NotEmpty(message = "deve ter ao menos um item")
    val itens: List<@Valid ItemRequest>,
    @field:PositiveOrZero(message = "não pode ser negativo")
    @field:DuasCasasDecimais
    val valorFrete: BigDecimal,
)

@DocumentoCompativel
data class ClienteRequest(
    val tipoPessoa: TipoPessoa,
    val documento: String,
    @field:NotBlank(message = "é obrigatório")
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
    @field:NotBlank(message = "é obrigatório")
    val idItem: String,
    val descricao: String,
    @field:Positive(message = "deve ser maior que zero")
    val quantidade: Int,
    @field:Positive(message = "deve ser maior que zero")
    @field:DuasCasasDecimais
    val valorUnitario: BigDecimal,
)

enum class TipoPessoa {
    FISICA,
    JURIDICA,
}
