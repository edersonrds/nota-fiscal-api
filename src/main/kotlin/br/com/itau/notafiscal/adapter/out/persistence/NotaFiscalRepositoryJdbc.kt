package br.com.itau.notafiscal.adapter.out.persistence

import br.com.itau.notafiscal.application.exception.PedidoJaEmitidoException
import br.com.itau.notafiscal.application.port.out.NotaFiscalRepository
import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.PessoaFisica
import br.com.itau.notafiscal.domain.model.PessoaJuridica
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Repository
class NotaFiscalRepositoryJdbc(
    private val jdbc: JdbcClient,
) : NotaFiscalRepository {
    @Transactional
    override fun salvar(nota: NotaFiscal) {
        if (inserirNota(nota) == 0) {
            val numeroExistente = checkNotNull(numeroDaNotaDoPedido(nota.pedido.idPedido))
            throw PedidoJaEmitidoException(nota.pedido.idPedido, numeroExistente)
        }
        nota.tributacao.itens.forEachIndexed { indice, itemTributado ->
            jdbc
                .sql(INSERIR_ITEM)
                .param("numeroNota", nota.numero)
                .param("sequencia", indice + 1)
                .param("idItem", itemTributado.item.idItem)
                .param("descricao", itemTributado.item.descricao)
                .param("quantidade", itemTributado.item.quantidade)
                .param("valorUnitario", itemTributado.item.valorUnitario)
                .param("valorTotal", itemTributado.item.valorTotal)
                .param("valorTributo", itemTributado.valorTributo)
                .update()
        }
    }

    override fun numeroDaNotaDoPedido(idPedido: String): Long? =
        jdbc
            .sql("select numero from nota_fiscal where id_pedido = :idPedido")
            .param("idPedido", idPedido)
            .query(Long::class.javaObjectType)
            .optional()
            .getOrNull()

    private fun inserirNota(nota: NotaFiscal): Int {
        val cliente = nota.pedido.cliente
        return jdbc
            .sql(INSERIR_NOTA)
            .param("numero", nota.numero)
            .param("idPedido", nota.pedido.idPedido)
            .param("dataPedido", nota.pedido.data)
            .param("dataEmissao", nota.dataEmissao)
            .param(
                "tipoPessoa",
                when (cliente) {
                    is PessoaFisica -> "FISICA"
                    is PessoaJuridica -> "JURIDICA"
                },
            ).param("documentoCliente", cliente.documento)
            .param("nomeCliente", cliente.nome)
            .param("regimeTributario", (cliente as? PessoaJuridica)?.regimeTributario?.name)
            .param("aliquota", nota.tributacao.aliquota)
            .param("regiaoEntrega", nota.frete.regiao.name)
            .param("freteValorBase", nota.frete.valorBase)
            .param("fretePercentual", nota.frete.percentual)
            .param("freteValor", nota.frete.valor)
            .param("totalItens", nota.totalItens)
            .param("totalTributos", nota.totalTributos)
            .param("totalNota", nota.totalNota)
            .update()
    }

    private companion object {
        const val INSERIR_NOTA = """
            insert into nota_fiscal (
                numero, id_pedido, data_pedido, data_emissao, tipo_pessoa, documento_cliente, nome_cliente,
                regime_tributario, aliquota, regiao_entrega, frete_valor_base, frete_percentual, frete_valor,
                total_itens, total_tributos, total_nota
            ) values (
                :numero, :idPedido, :dataPedido, :dataEmissao, :tipoPessoa, :documentoCliente, :nomeCliente,
                :regimeTributario, :aliquota, :regiaoEntrega, :freteValorBase, :fretePercentual, :freteValor,
                :totalItens, :totalTributos, :totalNota
            )
            on conflict (id_pedido) do nothing
        """

        const val INSERIR_ITEM = """
            insert into item_nota_fiscal (
                numero_nota, sequencia, id_item, descricao, quantidade, valor_unitario, valor_total, valor_tributo
            ) values (
                :numeroNota, :sequencia, :idItem, :descricao, :quantidade, :valorUnitario, :valorTotal, :valorTributo
            )
        """
    }
}
