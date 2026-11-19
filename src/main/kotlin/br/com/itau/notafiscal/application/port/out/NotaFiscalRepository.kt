package br.com.itau.notafiscal.application.port.out

import br.com.itau.notafiscal.domain.model.NotaFiscal

interface NotaFiscalRepository {
    fun salvar(nota: NotaFiscal)

    fun numeroDaNotaDoPedido(idPedido: String): Long?
}
