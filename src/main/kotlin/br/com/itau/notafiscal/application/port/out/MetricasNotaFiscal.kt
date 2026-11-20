package br.com.itau.notafiscal.application.port.out

import br.com.itau.notafiscal.domain.model.NotaFiscal

interface MetricasNotaFiscal {
    fun notaEmitida(nota: NotaFiscal)

    fun emissaoRecusada(codigo: String)

    fun falhaDeNotificacao(sistema: String)
}
