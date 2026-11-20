package br.com.itau.notafiscal.application.service

import br.com.itau.notafiscal.application.port.out.MetricasNotaFiscal
import br.com.itau.notafiscal.application.port.out.SistemaEntrega
import br.com.itau.notafiscal.application.port.out.SistemaEstoque
import br.com.itau.notafiscal.application.port.out.SistemaFinanceiro
import br.com.itau.notafiscal.application.port.out.SistemaRegistro
import br.com.itau.notafiscal.domain.model.NotaFiscal
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class NotificadorSistemas(
    private val estoque: SistemaEstoque,
    private val registro: SistemaRegistro,
    private val entrega: SistemaEntrega,
    private val financeiro: SistemaFinanceiro,
    private val metricas: MetricasNotaFiscal,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun notificar(nota: NotaFiscal) {
        val notificacoes =
            nota.tributacao.itens.map { item ->
                Notificacao("estoque", "item ${item.item.idItem}") { estoque.baixar(nota.numero, item) }
            } +
                listOf(
                    Notificacao("registro") { registro.registrar(nota) },
                    Notificacao("entrega") { entrega.agendar(nota) },
                    Notificacao("financeiro") { financeiro.lancar(nota) },
                )
        val contextoDeLog = MDC.getCopyOfContextMap().orEmpty()

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            notificacoes
                .map { notificacao -> executor.submit(Callable { executarComContexto(contextoDeLog, nota, notificacao) }) }
                .forEach { it.get() }
        }
    }

    private fun executarComContexto(
        contextoDeLog: Map<String, String>,
        nota: NotaFiscal,
        notificacao: Notificacao,
    ) {
        MDC.setContextMap(contextoDeLog)
        try {
            notificacao.chamada()
        } catch (erro: Exception) {
            metricas.falhaDeNotificacao(notificacao.sistema)
            log.error("Falha ao notificar {} ({}) sobre a nota {}", notificacao.sistema, notificacao.detalhe, nota.numero, erro)
        } finally {
            MDC.clear()
        }
    }

    private class Notificacao(
        val sistema: String,
        val detalhe: String = "nota",
        val chamada: () -> Unit,
    )
}
