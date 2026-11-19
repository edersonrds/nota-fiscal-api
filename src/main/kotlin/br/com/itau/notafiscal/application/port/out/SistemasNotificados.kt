package br.com.itau.notafiscal.application.port.out

import br.com.itau.notafiscal.domain.model.ItemTributado
import br.com.itau.notafiscal.domain.model.NotaFiscal

fun interface SistemaEstoque {
    fun baixar(
        numeroNota: Long,
        item: ItemTributado,
    )
}

fun interface SistemaRegistro {
    fun registrar(nota: NotaFiscal)
}

fun interface SistemaEntrega {
    fun agendar(nota: NotaFiscal)
}

fun interface SistemaFinanceiro {
    fun lancar(nota: NotaFiscal)
}
