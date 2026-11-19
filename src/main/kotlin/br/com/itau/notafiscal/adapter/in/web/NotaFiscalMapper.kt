package br.com.itau.notafiscal.adapter.`in`.web

import br.com.itau.notafiscal.domain.model.Cliente
import br.com.itau.notafiscal.domain.model.Endereco
import br.com.itau.notafiscal.domain.model.Frete
import br.com.itau.notafiscal.domain.model.Item
import br.com.itau.notafiscal.domain.model.ItemTributado
import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.Pedido
import br.com.itau.notafiscal.domain.model.PessoaFisica
import br.com.itau.notafiscal.domain.model.PessoaJuridica
import br.com.itau.notafiscal.domain.model.comEscalaMonetaria
import java.math.BigDecimal

fun EmitirNotaFiscalRequest.paraDominio() =
    Pedido(
        idPedido = idPedido,
        data = data,
        cliente = cliente.paraDominio(),
        itens = itens.map { it.paraDominio() },
        valorFrete = valorFrete,
    )

private fun ClienteRequest.paraDominio(): Cliente {
    val enderecosDoCliente = enderecos.map { it.paraDominio() }
    return when (tipoPessoa) {
        TipoPessoa.FISICA -> PessoaFisica(documento, nome, enderecosDoCliente)
        TipoPessoa.JURIDICA -> PessoaJuridica(documento, nome, enderecosDoCliente, regimeTributacao)
    }
}

private fun EnderecoRequest.paraDominio() = Endereco(finalidade, regiao, logradouro, numero, cidade, estado, cep)

private fun ItemRequest.paraDominio() = Item(idItem, descricao, quantidade, valorUnitario)

fun NotaFiscal.paraResposta() =
    NotaFiscalResponse(
        numeroNota = numero,
        idPedido = pedido.idPedido,
        dataEmissao = dataEmissao,
        cliente = pedido.cliente.paraResposta(),
        aliquotaPercentual = tributacao.aliquota.emPercentual(),
        itens = tributacao.itens.map { it.paraResposta() },
        frete = frete.paraResposta(),
        totalItens = totalItens,
        totalTributos = totalTributos,
        totalNota = totalNota,
    )

private fun Cliente.paraResposta() =
    when (this) {
        is PessoaFisica -> ClienteResponse(TipoPessoa.FISICA, documento, nome, regimeTributacao = null)
        is PessoaJuridica -> ClienteResponse(TipoPessoa.JURIDICA, documento, nome, regimeTributario)
    }

private fun ItemTributado.paraResposta() =
    ItemResponse(
        idItem = item.idItem,
        descricao = item.descricao,
        quantidade = item.quantidade,
        valorUnitario = item.valorUnitario.comEscalaMonetaria(),
        valorTotal = item.valorTotal,
        valorTributo = valorTributo,
    )

private fun Frete.paraResposta() =
    FreteResponse(
        regiao = regiao,
        valorBase = valorBase,
        percentual = percentual.emPercentual(),
        valor = valor,
    )

private fun BigDecimal.emPercentual() = movePointRight(2).comEscalaMonetaria()
