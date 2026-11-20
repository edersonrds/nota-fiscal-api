package br.com.itau.notafiscal.application

import br.com.itau.notafiscal.application.port.out.MetricasNotaFiscal
import br.com.itau.notafiscal.domain.model.NotaFiscal
import java.util.concurrent.ConcurrentLinkedQueue

class MetricasEmMemoria : MetricasNotaFiscal {
    val emitidas = ConcurrentLinkedQueue<Long>()
    val recusas = ConcurrentLinkedQueue<String>()
    val falhasDeNotificacao = ConcurrentLinkedQueue<String>()

    override fun notaEmitida(nota: NotaFiscal) {
        emitidas.add(nota.numero)
    }

    override fun emissaoRecusada(codigo: String) {
        recusas.add(codigo)
    }

    override fun falhaDeNotificacao(sistema: String) {
        falhasDeNotificacao.add(sistema)
    }
}
