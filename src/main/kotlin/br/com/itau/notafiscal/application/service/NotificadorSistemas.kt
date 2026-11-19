package br.com.itau.notafiscal.application.service

import br.com.itau.notafiscal.application.port.out.SistemaEntrega
import br.com.itau.notafiscal.application.port.out.SistemaEstoque
import br.com.itau.notafiscal.application.port.out.SistemaFinanceiro
import br.com.itau.notafiscal.application.port.out.SistemaRegistro
import br.com.itau.notafiscal.domain.model.NotaFiscal
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class NotificadorSistemas(
    private val estoque: SistemaEstoque,
    private val registro: SistemaRegistro,
    private val entrega: SistemaEntrega,
    private val financeiro: SistemaFinanceiro,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun notificar(nota: NotaFiscal) {
        val notificacoes =
            nota.tributacao.itens.map { item -> Notificacao("estoque (item ${item.item.idItem})") { estoque.baixar(nota.numero, item) } } +
                listOf(
                    Notificacao("registro") { registro.registrar(nota) },
                    Notificacao("entrega") { entrega.agendar(nota) },
                    Notificacao("financeiro") { financeiro.lancar(nota) },
                )

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            notificacoes
                .map { notificacao -> executor.submit(Callable { executar(nota, notificacao) }) }
                .forEach { it.get() }
        }
    }

    private fun executar(
        nota: NotaFiscal,
        notificacao: Notificacao,
    ) {
        try {
            notificacao.chamada()
        } catch (erro: Exception) {
            log.error("Falha ao notificar {} sobre a nota {}", notificacao.destino, nota.numero, erro)
        }
    }

    private class Notificacao(
        val destino: String,
        val chamada: () -> Unit,
    )
}
